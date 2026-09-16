#include <pebble.h>

// Weather face for the plugin API: the time, and every reading the weather plugin has for the
// user's first saved location — temperature, the day's high and low, the condition, the chance
// of rain, the UV index, and the next few hours. Layout follows the firmware's weather app: a
// hero reading, a condition disc, and a segmented UV meter.
//
// PKJS sends one app message with all of it; nothing here talks to the plugin directly. The last
// message is kept in persistent storage, so a face that opens away from the phone shows the last
// forecast rather than nothing.

// Sized to what each reading actually is, because the whole record has to fit in one
// persisted value — see the assert below.
#define NAME_MAX 32
#define VALUE_MAX 10
#define ERROR_MAX 20
// 4-bpp, so a square of this side is ICON_SIZE_MAX * ICON_SIZE_MAX / 2 bytes on the wire.
#define ICON_SIZE_MAX 96
#define ICONS (HOURS + 1)
#define UV_SEGMENTS 6
#define HOURS 3
#define UNKNOWN_UV -1
#define PERSIST_WEATHER 1
#define PERSIST_DARK 2

static bool s_dark;

/** The two colours everything else is built from, so a theme is one flag rather than a fork. */
static GColor ink(void) { return s_dark ? GColorWhite : GColorBlack; }
static GColor paper(void) { return s_dark ? GColorBlack : GColorWhite; }

/** Text that shouldn't shout: the high/low, the place, the empty half of the UV meter. */
static GColor muted(void) {
  return PBL_IF_COLOR_ELSE(s_dark ? GColorLightGray : GColorDarkGray, ink());
}

#define ACCENT PBL_IF_COLOR_ELSE(GColorOrange, ink())

typedef struct {
  char place[NAME_MAX];
  char condition[NAME_MAX];
  char temp[VALUE_MAX];
  char high[VALUE_MAX];
  char low[VALUE_MAX];
  char precip[VALUE_MAX];
  char hour_temp[HOURS][VALUE_MAX];
  char error[ERROR_MAX];
  int uv;
} Weather;

// The cache is one persisted value, and the watch caps those: a record that outgrows the limit
// isn't truncated, it simply never writes, and the face comes up blank away from the phone.
_Static_assert(sizeof(Weather) <= PERSIST_DATA_MAX_LENGTH, "the weather record must fit a persist");

static Window *s_window;
static Layer *s_canvas;
// Slot 0 is the hero glyph; 1..3 are the hours.
static GBitmap *s_icons[ICONS];
static Weather s_weather = { .uv = UNKNOWN_UV };
static char s_time[8];

// ---------------------------------------------------------------- glyphs

/** 4-bpp palettised, exactly as the plugin API hands it over. */
static void set_icon(int slot, const Tuple *width, const Tuple *height,
                     const Tuple *palette, const Tuple *pixels) {
  GSize size = GSize(width->value->int32, height->value->int32);
  if (size.w <= 0 || size.h <= 0 || size.w > ICON_SIZE_MAX || size.h > ICON_SIZE_MAX) return;
  if (s_icons[slot]) {
    gbitmap_destroy(s_icons[slot]);
    s_icons[slot] = NULL;
  }
  // The palette outlives the call, so the bitmap owns a copy of it.
  uint8_t *owned_palette = malloc(palette->length);
  if (!owned_palette) return;
  memcpy(owned_palette, palette->value->data, palette->length);
  GBitmap *bitmap = gbitmap_create_blank_with_palette(size, GBitmapFormat4BitPalette,
                                                      (GColor *)owned_palette, true);
  if (!bitmap) {
    free(owned_palette);
    return;
  }
  // Rows are padded to ceil(width / 2) on the wire, but to whatever the watch wants in memory.
  uint16_t wire_stride = (size.w + 1) / 2;
  uint16_t stride = gbitmap_get_bytes_per_row(bitmap);
  uint8_t *data = gbitmap_get_data(bitmap);
  for (int row = 0; row < size.h; row++) {
    uint32_t offset = row * wire_stride;
    if (offset + wire_stride > pixels->length) break;
    memcpy(data + row * stride, pixels->value->data + offset, wire_stride);
  }
  s_icons[slot] = bitmap;
}

static void draw_icon(GContext *ctx, GRect box, GBitmap *icon) {
  if (!icon) {
    // Nothing has arrived yet — an outline says where it will be.
    graphics_context_set_stroke_color(ctx, muted());
    graphics_draw_circle(ctx, grect_center_point(&box), box.size.w / 2 - 1);
    graphics_context_set_stroke_color(ctx, ink());
    return;
  }
  GSize size = gbitmap_get_bounds(icon).size;
  GRect frame = GRect(box.origin.x + (box.size.w - size.w) / 2,
                      box.origin.y + (box.size.h - size.h) / 2, size.w, size.h);
  // GCompOpAssign would paint the glyph's transparent ground as black.
  graphics_context_set_compositing_mode(ctx, GCompOpSet);
  graphics_draw_bitmap_in_rect(ctx, icon, frame);
  graphics_context_set_compositing_mode(ctx, GCompOpAssign);
}

// ---------------------------------------------------------------- the face

static GPath *s_droplet;
static const GPathInfo DROPLET_OUTLINE = {
  .num_points = 3,
  .points = (GPoint[]) {{ 0, -9 }, { 4, -1 }, { -4, -1 }},
};

/**
 * How far a row has to come in from the edge to stay on the screen: zero on a rectangle, and on
 * a round watch the wider of what its top and bottom edges need.
 */
static int band_inset(GRect bounds, int y, int h) {
#ifdef PBL_ROUND
  int radius = bounds.size.w / 2;
  int centre = bounds.origin.y + bounds.size.h / 2;
  int top = y - centre;
  int bottom = y + h - centre;
  if (top < 0) top = -top;
  if (bottom < 0) bottom = -bottom;
  int furthest = top > bottom ? top : bottom;
  if (furthest >= radius) return radius;
  int half = 0;
  while ((half + 1) * (half + 1) + furthest * furthest <= radius * radius) half++;
  return radius - half;
#else
  return 0;
#endif
}

/** The part of a row that is actually on the screen. */
static GRect band(GRect bounds, int y, int h) {
  int inset = band_inset(bounds, y, h);
  return GRect(bounds.origin.x + inset, y, bounds.size.w - inset * 2, h);
}

/** How wide a string renders, so a row can centre a group of parts rather than a box. */
static int text_width(const char *text, GFont font) {
  return graphics_text_layout_get_content_size(text, font, GRect(0, 0, 200, 20),
                                               GTextOverflowModeFill, GTextAlignmentLeft).w;
}

/** Segments filled to the reading, the way the firmware's weather app draws it. */
static void draw_uv_meter(GContext *ctx, GRect half, int uv) {
  GFont font = fonts_get_system_font(half.size.h >= 20 ? FONT_KEY_GOTHIC_18_BOLD
                                                       : FONT_KEY_GOTHIC_14_BOLD);
  int label_w = text_width("UV", font) + 4;
  int number_w = text_width("00", font);
  int step = half.size.h / 2;
  // Laid out as one group and centred, rather than pushed to the edges of the half.
  int group_w = label_w + UV_SEGMENTS * step + 2 + number_w;
  GRect row = GRect(half.origin.x + (half.size.w - group_w) / 2, half.origin.y,
                    group_w, half.size.h);

  graphics_draw_text(ctx, "UV", font, GRect(row.origin.x, row.origin.y - 3, label_w, row.size.h + 4),
                     GTextOverflowModeFill, GTextAlignmentLeft, NULL);

  GRect meter = GRect(row.origin.x + label_w, row.origin.y + 2,
                      UV_SEGMENTS * step, row.size.h - 4);
  // The scale runs to 11; each segment is two points of it, and the top one catches the rest.
  int lit = uv < 0 ? 0 : (uv + 1) / 2;
  if (lit > UV_SEGMENTS) lit = UV_SEGMENTS;
  for (int i = 0; i < UV_SEGMENTS; i++) {
    GRect box = GRect(meter.origin.x + i * step, meter.origin.y, step - 2, meter.size.h);
    if (i < lit) {
      graphics_context_set_fill_color(ctx, ACCENT);
      graphics_fill_rect(ctx, box, 1, GCornersAll);
    } else {
      graphics_context_set_stroke_color(ctx, muted());
      graphics_draw_rect(ctx, box);
      graphics_context_set_stroke_color(ctx, ink());
    }
  }
  graphics_context_set_fill_color(ctx, ink());

  char reading[8];
  if (uv < 0) {
    snprintf(reading, sizeof(reading), "--");
  } else {
    snprintf(reading, sizeof(reading), "%d", uv > 99 ? 99 : uv);
  }
  graphics_draw_text(ctx, reading, font,
                     GRect(row.origin.x + row.size.w - number_w, row.origin.y - 3,
                           number_w, row.size.h + 4),
                     GTextOverflowModeFill, GTextAlignmentRight, NULL);
}

static void draw_precip(GContext *ctx, GRect half, const char *percent) {
  if (!percent[0]) return;
  GFont font = fonts_get_system_font(half.size.h >= 20 ? FONT_KEY_GOTHIC_18_BOLD
                                                       : FONT_KEY_GOTHIC_14_BOLD);
  int drop_w = 14;
  int group_w = drop_w + text_width(percent, font);
  int x = half.origin.x + (half.size.w - group_w) / 2;

  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorBlueMoon, ink()));
  GPoint drop = GPoint(x + 5, half.origin.y + half.size.h / 2 + 1);
  gpath_move_to(s_droplet, drop);
  gpath_draw_filled(ctx, s_droplet);
  graphics_fill_circle(ctx, drop, 4);
  graphics_context_set_fill_color(ctx, ink());

  graphics_draw_text(ctx, percent, font,
                     GRect(x + drop_w, half.origin.y - 3, group_w - drop_w, half.size.h + 4),
                     GTextOverflowModeFill, GTextAlignmentLeft, NULL);
}

/** The next few hours: the same discs as the hero, small, with what it will be under each. */
static void draw_hourly(GContext *ctx, GRect row, const Weather *weather, int disc) {
  int column = row.size.w / HOURS;
  int label_h = row.size.h - disc;
  GFont font = fonts_get_system_font(label_h >= 20 ? FONT_KEY_GOTHIC_18 : FONT_KEY_GOTHIC_14);
  for (int i = 0; i < HOURS; i++) {
    if (!weather->hour_temp[i][0]) continue;
    int centre_x = row.origin.x + column * i + column / 2;
    draw_icon(ctx, GRect(centre_x - disc / 2, row.origin.y, disc, disc), s_icons[i + 1]);
    graphics_draw_text(ctx, weather->hour_temp[i], font,
                       GRect(row.origin.x + column * i, row.origin.y + disc, column, label_h),
                       GTextOverflowModeFill, GTextAlignmentCenter, NULL);
  }
}

static void draw_rule(GContext *ctx, GRect bounds, int y) {
  GRect row = band(bounds, y, 1);
  graphics_context_set_stroke_color(ctx, muted());
  graphics_draw_line(ctx, GPoint(row.origin.x + 6, y),
                     GPoint(row.origin.x + row.size.w - 6, y));
  graphics_context_set_stroke_color(ctx, ink());
}

/**
 * Everything below the body is a fixed height; the body takes whatever is left, so the hero
 * reading grows with the screen rather than the furniture around it. Worked out in one place
 * because the phone needs the glyph sizes before anything is drawn.
 */
typedef struct {
  int pad;
  int time_y;
  int time_h;
  int place_y;
  int body_top;
  int body_h;
  int meter_y;
  int meter_h;
  int hourly_y;
  int hourly_h;
  int place_h;
  int hero;
  int hour_disc;
} Layout;

/**
 * Every band is a fraction of the screen, so the same face fills a 168px Pebble and a 260px
 * round one; the body takes whatever the furniture leaves, and the hero disc takes the body.
 */
static Layout layout_for(GRect bounds) {
  int h = bounds.size.h;
  Layout out;
  out.pad = h / 36;
  out.time_h = h * 11 / 50;
  out.meter_h = h / 12;
  out.hourly_h = h * 3 / 20;
  out.place_h = h / 15;
  // A circle has no room at its top or bottom, so the whole stack is pulled in and the place
  // moves up under the time; on a rectangle the place is the footer and the body starts higher.
  int margin = PBL_IF_ROUND_ELSE(h / 22, 2);
  out.time_y = bounds.origin.y + margin;
  out.place_y = PBL_IF_ROUND_ELSE(out.time_y + out.time_h + 2, 0);
  out.body_top = PBL_IF_ROUND_ELSE(out.place_y + out.place_h + out.pad,
                                   out.time_y + out.time_h + out.pad * 2);
  out.body_h = h - out.body_top - out.meter_h - out.hourly_h - out.pad * 3 -
               PBL_IF_ROUND_ELSE(h / 15, out.place_h);
  out.hero = out.body_h > bounds.size.w / 2 ? bounds.size.w / 2 : out.body_h;
  out.meter_y = out.body_top + out.body_h + out.pad;
  out.hourly_y = out.meter_y + out.meter_h + out.pad;

  int column = band(bounds, out.hourly_y, out.hourly_h).size.w / HOURS;
  out.hour_disc = out.hourly_h * 3 / 5;
  if (out.hour_disc > column - 8) out.hour_disc = column - 8;
  return out;
}

static void update_face(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  Layout layout = layout_for(bounds);
  int time_h = layout.time_h;
  int meter_h = layout.meter_h;
  int hourly_h = layout.hourly_h;
  int place_h = layout.place_h;
  int pad = layout.pad;
  graphics_context_set_antialiased(ctx, true);
  graphics_context_set_text_color(ctx, ink());

  // Big text sits low in its box, so the row is only as narrow as the glyphs themselves need.
  int time_inset = band_inset(bounds, layout.time_y + time_h / 3, time_h - time_h / 3);
  GRect time_box = GRect(bounds.origin.x + time_inset, layout.time_y,
                         bounds.size.w - time_inset * 2, time_h);
  graphics_draw_text(ctx, s_time,
                     fonts_get_system_font(time_h >= 48 ? FONT_KEY_ROBOTO_BOLD_SUBSET_49
                                           : time_h >= 42 ? FONT_KEY_BITHAM_42_BOLD
                                                          : FONT_KEY_BITHAM_30_BLACK),
                     time_box,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);

  char footer[NAME_MAX * 2 + 8];
  snprintf(footer, sizeof(footer), "%s%s%s", s_weather.condition,
           (s_weather.condition[0] && s_weather.place[0]) ? " \u00b7 " : "", s_weather.place);

#ifdef PBL_ROUND
  graphics_context_set_text_color(ctx, muted());
  graphics_draw_text(ctx, footer,
                     fonts_get_system_font(place_h >= 18 ? FONT_KEY_GOTHIC_18
                                                         : FONT_KEY_GOTHIC_14),
                     band(bounds, layout.place_y, place_h),
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
  graphics_context_set_text_color(ctx, ink());
#else
  draw_rule(ctx, bounds, bounds.origin.y + time_h + pad);
#endif

  int body_top = layout.body_top;
  int body_h = layout.body_h;

  if (s_weather.error[0]) {
    graphics_draw_text(ctx, s_weather.error, fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD),
                       band(bounds, body_top, body_h),
                       GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
    return;
  }

  // The disc and the readings beside it are one group, centred: on a round watch a row pinned
  // to the left edge is half off the screen.
  int disc = layout.hero;
  int top = body_top + (body_h - disc) / 2;
  // The two readings share the disc's height, and each takes the largest font its line fits.
  int temp_line = disc * 3 / 5;
  int range_line = disc - temp_line;
  GFont temp_font = fonts_get_system_font(temp_line >= 42 ? FONT_KEY_BITHAM_42_BOLD
                                        : temp_line >= 30 ? FONT_KEY_BITHAM_30_BLACK
                                        : temp_line >= 26 ? FONT_KEY_GOTHIC_28_BOLD
                                        : temp_line >= 22 ? FONT_KEY_GOTHIC_24_BOLD
                                                          : FONT_KEY_GOTHIC_18_BOLD);
  GFont range_font = fonts_get_system_font(range_line >= 24 ? FONT_KEY_GOTHIC_24_BOLD
                                         : range_line >= 18 ? FONT_KEY_GOTHIC_18_BOLD
                                                            : FONT_KEY_GOTHIC_14);

  char range[VALUE_MAX * 2 + 8];
  snprintf(range, sizeof(range), "%s / %s", s_weather.high, s_weather.low);
  bool has_range = s_weather.high[0] != '\0';

  int gap = bounds.size.w / 24;
  int text_w = text_width(s_weather.temp, temp_font);
  if (has_range) {
    int range_w = text_width(range, range_font);
    if (range_w > text_w) text_w = range_w;
  }
  GRect row = band(bounds, top, disc);
  int group_w = disc + gap + text_w;
  if (group_w > row.size.w) group_w = row.size.w;
  int x = row.origin.x + (row.size.w - group_w) / 2;

  draw_icon(ctx, GRect(x, top, disc, disc), s_icons[0]);

  int text_x = x + disc + gap;
  text_w = group_w - disc - gap;
  graphics_draw_text(ctx, s_weather.temp, temp_font,
                     GRect(text_x, top, text_w, temp_line),
                     GTextOverflowModeFill, GTextAlignmentLeft, NULL);

  graphics_context_set_text_color(ctx, muted());
  graphics_draw_text(ctx, has_range ? range : "", range_font,
                     GRect(text_x, top + temp_line - 2, text_w, range_line + 2),
                     GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  graphics_context_set_text_color(ctx, ink());

  // UV in the left half of its row, the chance of rain in the right, each centred in its own.
  GRect meter_row = grect_inset(band(bounds, layout.meter_y, meter_h), GEdgeInsets(0, 4));
  int half = meter_row.size.w / 2;
  draw_uv_meter(ctx, GRect(meter_row.origin.x, meter_row.origin.y, half, meter_h), s_weather.uv);
  draw_precip(ctx, GRect(meter_row.origin.x + half, meter_row.origin.y, half, meter_h),
              s_weather.precip);

  draw_hourly(ctx, band(bounds, layout.hourly_y, hourly_h), &s_weather, layout.hour_disc);

#ifndef PBL_ROUND
  // The condition phrase and where it is: the only line that says what this is a forecast for.
  graphics_context_set_text_color(ctx, muted());
  graphics_draw_text(ctx, footer, fonts_get_system_font(FONT_KEY_GOTHIC_14),
                     GRect(bounds.origin.x + 6, layout.hourly_y + hourly_h,
                           bounds.size.w - 12, place_h),
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
#endif
}

// ---------------------------------------------------------------- app

/** Formats only: the first call happens before there is a layer to invalidate. */
static void set_time(struct tm *now) {
  strftime(s_time, sizeof(s_time), clock_is_24h_style() ? "%H:%M" : "%I:%M", now);
}

static void tick(struct tm *now, TimeUnits units_changed) {
  set_time(now);
  layer_mark_dirty(s_canvas);
}

static void copy_field(DictionaryIterator *iter, uint32_t key, char *out, size_t size) {
  Tuple *tuple = dict_find(iter, key);
  if (tuple) snprintf(out, size, "%s", tuple->value->cstring);
}

/** Kept so a face opened away from the phone shows the last forecast rather than nothing. */
static void load_weather(void) {
  if (persist_exists(PERSIST_WEATHER) &&
      persist_get_size(PERSIST_WEATHER) == (int)sizeof(s_weather)) {
    persist_read_data(PERSIST_WEATHER, &s_weather, sizeof(s_weather));
  }
  s_dark = persist_read_bool(PERSIST_DARK);
}

static void send_icon_sizes(void);

static void inbox_received(DictionaryIterator *iter, void *context) {
  // PKJS is restarted whenever the phone reconnects, and comes back not knowing how big this
  // watch's glyphs are.
  if (dict_find(iter, MESSAGE_KEY_hello)) {
    send_icon_sizes();
    return;
  }
  Tuple *slot = dict_find(iter, MESSAGE_KEY_iconSlot);
  if (slot) {
    Tuple *width = dict_find(iter, MESSAGE_KEY_iconWidth);
    Tuple *height = dict_find(iter, MESSAGE_KEY_iconHeight);
    Tuple *palette = dict_find(iter, MESSAGE_KEY_iconPalette);
    Tuple *pixels = dict_find(iter, MESSAGE_KEY_iconPixels);
    int index = slot->value->int32;
    if (index >= 0 && index < ICONS && width && height && palette && pixels) {
      set_icon(index, width, height, palette, pixels);
      layer_mark_dirty(s_canvas);
    }
    return;
  }
  copy_field(iter, MESSAGE_KEY_place, s_weather.place, sizeof(s_weather.place));
  copy_field(iter, MESSAGE_KEY_temp, s_weather.temp, sizeof(s_weather.temp));
  copy_field(iter, MESSAGE_KEY_high, s_weather.high, sizeof(s_weather.high));
  copy_field(iter, MESSAGE_KEY_low, s_weather.low, sizeof(s_weather.low));
  copy_field(iter, MESSAGE_KEY_condition, s_weather.condition, sizeof(s_weather.condition));
  copy_field(iter, MESSAGE_KEY_err, s_weather.error, sizeof(s_weather.error));
  copy_field(iter, MESSAGE_KEY_precip, s_weather.precip, sizeof(s_weather.precip));
  copy_field(iter, MESSAGE_KEY_h0t, s_weather.hour_temp[0], VALUE_MAX);
  copy_field(iter, MESSAGE_KEY_h1t, s_weather.hour_temp[1], VALUE_MAX);
  copy_field(iter, MESSAGE_KEY_h2t, s_weather.hour_temp[2], VALUE_MAX);
  Tuple *uv = dict_find(iter, MESSAGE_KEY_uv);
  if (uv) s_weather.uv = uv->value->int32;
  Tuple *theme = dict_find(iter, MESSAGE_KEY_theme);
  if (theme) {
    s_dark = theme->value->int32 != 0;
    persist_write_bool(PERSIST_DARK, s_dark);
    window_set_background_color(s_window, paper());
  }
  persist_write_data(PERSIST_WEATHER, &s_weather, sizeof(s_weather));
  layer_mark_dirty(s_canvas);
}

/** Nothing on the watch can scale a bitmap, so the glyph sizes are the watch's to decide. */
static void send_icon_sizes(void) {
  Layout layout = layout_for(layer_get_bounds(s_canvas));
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_int(out, MESSAGE_KEY_heroSize, &layout.hero, sizeof(int), true);
  dict_write_int(out, MESSAGE_KEY_hourSize, &layout.hour_disc, sizeof(int), true);
  app_message_outbox_send();
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  s_canvas = layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_canvas, update_face);
  layer_add_child(root, s_canvas);
  send_icon_sizes();
}

static void window_unload(Window *window) {
  layer_destroy(s_canvas);
  for (int i = 0; i < ICONS; i++) {
    if (s_icons[i]) {
      gbitmap_destroy(s_icons[i]);
      s_icons[i] = NULL;
    }
  }
}

static void init(void) {
  load_weather();
  s_droplet = gpath_create(&DROPLET_OUTLINE);
  s_window = window_create();
  window_set_background_color(s_window, paper());
  window_set_window_handlers(s_window, (WindowHandlers){
      .load = window_load,
      .unload = window_unload,
  });
  app_message_register_inbox_received(inbox_received);
  // A hero glyph is 4-bpp: an 88px square is nearly 4KB of pixels.
  app_message_open(app_message_inbox_size_maximum(), 64);
  tick_timer_service_subscribe(MINUTE_UNIT, tick);
  time_t now = time(NULL);
  set_time(localtime(&now));
  window_stack_push(s_window, true);
}

static void deinit(void) {
  gpath_destroy(s_droplet);
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
