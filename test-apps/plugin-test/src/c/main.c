#include <pebble.h>

// Widget shell for the plugin API test: four quadrants, each showing one plugin source.
// Which source, which instance and which shape is chosen on the app's settings page.
//
// PKJS sends one string per quadrant, '|'-separated, tagged with the shape it carries:
//   t|<label>|<text>                       short or long text
//   r|<label>|<value>|<min>|<max>|<unit>   numeric value with a range, drawn as a bar
//   b|<label>|<0|1>                        boolean, drawn as a checkbox
//   m|<label>|<w>x<h>                      an icon or artwork, whose pixels arrive in
//                                          their own message
//                                          (imgQuad/imgWidth/imgHeight/imgPalette/imgPixels)
//
// Nothing on the watch can scale a bitmap, so the size artwork is useful at is the watch's to
// decide: it sends `artSize` — as much of the tile as is left under a title — on load and
// whenever a (re)started PKJS says hello, and PKJS asks the plugin for that.
//
// An upper-case tag means the quadrant has an action bound to it: it is outlined, and tapping
// it asks PKJS to invoke that action. A `+` after the tag means tappable without the outline —
// the tap changes what the quadrant shows rather than acting on anything.

// Eight readings over two pages of four; UP and DOWN turn the page.
#define QUADRANTS 8
#define TILES 4
#define PAGES (QUADRANTS / TILES)
#define PERSIST_DARK 1
#define PERSIST_QUICK 2
// One key per quadrant: a persisted value is capped, and eight payloads don't fit in one.
#define PERSIST_QUAD 10
#define PAYLOAD_MAX 128

/**
 * Which quick-launch button this app sits on. `launch_reason()` says we were quick-launched but
 * not from which button, so the phone tells us the slot and the watch decides what to do with it.
 */
typedef enum {
  QUICK_NONE = 0,
  QUICK_UP = 1,
  QUICK_DOWN = 2,
} QuickSlot;

static QuickSlot s_quick_slot;
static bool s_quick_launched;

/** True when this launch was the quick-launch press the user configured us for. */
static bool paging_out(void) {
  return s_quick_launched && s_quick_slot != QUICK_NONE;
}
#define FIELD_MAX 32
// Pebble draws bold or it doesn't read at arm's length.
#define STROKE 3
static bool s_dark;

/** The two colours everything else is built from, so a theme is one flag rather than a fork. */
static GColor ink(void) { return s_dark ? GColorWhite : GColorBlack; }
static GColor paper(void) { return s_dark ? GColorBlack : GColorWhite; }

/** Furniture that shouldn't shout: the grid, an empty gauge, a title line. */
static GColor muted(void) {
  return PBL_IF_COLOR_ELSE(s_dark ? GColorLightGray : GColorDarkGray, ink());
}

#define ACCENT PBL_IF_COLOR_ELSE(GColorOrange, ink())
// 4-bpp, so a square of this side is ART_SIZE_MAX * ART_SIZE_MAX / 2 bytes on the wire — the
// ceiling is the app message inbox, which is 8KB on current firmware.
#define ART_SIZE_MAX 120
// The title line, and so the height artwork can't have.
#define TITLE_HEIGHT 16
// The actionable outline is drawn inside the tile's edges, so it is what artwork has to stop
// short of.
#define OUTLINE_INSET 2
#define OUTLINE_STROKE 2
#define OUTLINE_ROOM (OUTLINE_INSET + OUTLINE_STROKE)

typedef struct {
  int index;
  char shape;
  bool tappable;
  bool outlined;
  char label[FIELD_MAX];
  char text[FIELD_MAX];
  int value;
  int min;
  int max;
  bool on;
} Quadrant;

static Window *s_window;
static Layer *s_grid;
static GBitmap *s_art[QUADRANTS];
// A palette of two entries or fewer is a monochrome icon, which is drawn in the theme's ink
// rather than in whatever colour it happened to arrive in. Zero for anything else.
static uint8_t s_art_mono[QUADRANTS];
static Layer *s_layers[TILES];
static GRect s_frames[TILES];
static Quadrant s_quadrants[QUADRANTS];
static int s_page;

/** Which of the eight a tile is showing, given the page it is on. */
static int slot_of(int tile) {
  return s_page * TILES + tile;
}

static void mark_page_dirty(void);

static uint32_t quad_key(int index) {
  switch (index) {
    case 0: return MESSAGE_KEY_quad0;
    case 1: return MESSAGE_KEY_quad1;
    case 2: return MESSAGE_KEY_quad2;
    case 3: return MESSAGE_KEY_quad3;
    case 4: return MESSAGE_KEY_quad4;
    case 5: return MESSAGE_KEY_quad5;
    case 6: return MESSAGE_KEY_quad6;
    default: return MESSAGE_KEY_quad7;
  }
}

// ---------------------------------------------------------------- parsing

/** Copies up to the next '|' (or the end) into out, and returns the rest. */
static const char *take_field(const char *in, char *out, size_t size) {
  size_t taken = 0;
  while (*in && *in != '|' && taken < size - 1) {
    out[taken++] = *in++;
  }
  out[taken] = '\0';
  while (*in && *in != '|') in++;  // skip anything that didn't fit
  return *in == '|' ? in + 1 : in;
}

static void parse(const char *payload, Quadrant *quadrant) {
  char field[FIELD_MAX];
  memset(quadrant, 0, sizeof(*quadrant));
  quadrant->max = 100;

  payload = take_field(payload, field, sizeof(field));
  quadrant->outlined = field[0] >= 'A' && field[0] <= 'Z';
  quadrant->shape = quadrant->outlined ? field[0] - 'A' + 'a' : field[0];
  quadrant->tappable = quadrant->outlined || field[1] == '+';
  payload = take_field(payload, quadrant->label, sizeof(quadrant->label));

  switch (quadrant->shape) {
    case 'r':
      payload = take_field(payload, field, sizeof(field));
      quadrant->value = atoi(field);
      payload = take_field(payload, field, sizeof(field));
      quadrant->min = atoi(field);
      payload = take_field(payload, field, sizeof(field));
      quadrant->max = atoi(field);
      char unit[8];
      take_field(payload, unit, sizeof(unit));
      snprintf(quadrant->text, sizeof(quadrant->text), "%d%s", quadrant->value, unit);
      break;
    case 'b':
      take_field(payload, field, sizeof(field));
      quadrant->on = field[0] == '1';
      break;
    default:
      take_field(payload, quadrant->text, sizeof(quadrant->text));
      break;
  }
}

// ---------------------------------------------------------------- drawing

static void draw_bar(GContext *ctx, GRect bounds, const Quadrant *quadrant) {
  int span = quadrant->max - quadrant->min;
  int filled = span > 0 ? ((quadrant->value - quadrant->min) * bounds.size.w) / span : 0;
  int radius = bounds.size.h / 2;
  if (filled < 0) filled = 0;
  if (filled > bounds.size.w) filled = bounds.size.w;
  // Any reading above the minimum shows as at least a dot, rather than as an empty track.
  if (filled > 0 && filled < bounds.size.h) filled = bounds.size.h;
  // An empty track that still reads as a track, and a solid accent fill.
  graphics_context_set_fill_color(ctx,
                                 PBL_IF_COLOR_ELSE(s_dark ? GColorDarkGray : GColorLightGray, paper()));
  graphics_fill_rect(ctx, bounds, radius, GCornersAll);
  graphics_context_set_fill_color(ctx, ACCENT);
  graphics_fill_rect(ctx, GRect(bounds.origin.x, bounds.origin.y, filled, bounds.size.h), radius,
                     GCornersAll);
  graphics_context_set_stroke_color(ctx, ink());
  graphics_context_set_stroke_width(ctx, 1);
  graphics_draw_round_rect(ctx, bounds, radius);
}

static void draw_checkbox(GContext *ctx, GRect bounds, bool on) {
  // On: a solid accent block with a thick knocked-out tick. Off: a heavy empty box. Both read
  // at a glance from across a room, which a hairline outline does not.
  graphics_context_set_stroke_width(ctx, STROKE);
  if (on) {
    graphics_context_set_fill_color(ctx, ACCENT);
    graphics_fill_rect(ctx, bounds, 5, GCornersAll);
    graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(GColorWhite, paper()));
  } else {
    graphics_context_set_stroke_color(ctx, ink());
    graphics_draw_round_rect(ctx, bounds, 5);
    graphics_context_set_stroke_width(ctx, 1);
    return;
  }
  // A tick, drawn from the box's own proportions so it scales with the display.
  int inset = bounds.size.w / 4;
  GPoint left = GPoint(bounds.origin.x + inset, bounds.origin.y + bounds.size.h / 2);
  GPoint bottom = GPoint(bounds.origin.x + bounds.size.w / 2,
                         bounds.origin.y + bounds.size.h - inset);
  GPoint right = GPoint(bounds.origin.x + bounds.size.w - inset, bounds.origin.y + inset);
  graphics_draw_line(ctx, left, bottom);
  graphics_draw_line(ctx, bottom, right);
  graphics_context_set_stroke_width(ctx, 1);
  graphics_context_set_stroke_color(ctx, ink());
}

/** 4-bpp palettised, exactly as the plugin API hands it over. */
static void set_art(int index, const Tuple *width, const Tuple *height,
                    const Tuple *palette, const Tuple *pixels) {
  if (s_art[index]) {
    gbitmap_destroy(s_art[index]);
    s_art[index] = NULL;
  }
  GSize size = GSize(width->value->int32, height->value->int32);
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
  s_art[index] = bitmap;
  s_art_mono[index] = palette->length <= 2 ? palette->length : 0;
}

static void draw_art(GContext *ctx, GRect bounds, GBitmap *art, uint8_t mono_entries) {
  if (!art) return;
  GColor *palette = gbitmap_get_palette(art);
  for (int i = 0; i < mono_entries; i++) {
    if (!gcolor_equal(palette[i], GColorClear)) palette[i] = ink();
  }
  GSize size = gbitmap_get_bounds(art).size;
  GRect frame = GRect(bounds.origin.x + (bounds.size.w - size.w) / 2,
                      bounds.origin.y + (bounds.size.h - size.h) / 2, size.w, size.h);
  // GCompOpAssign would paint the palette's transparent entries as black.
  graphics_context_set_compositing_mode(ctx, GCompOpSet);
  graphics_draw_bitmap_in_rect(ctx, art, frame);
  graphics_context_set_compositing_mode(ctx, GCompOpAssign);
}

/** How tall the text renders, so a tile can centre it instead of top-aligning it. */
static int measure(const char *text, GFont font, GRect box, GTextOverflowMode overflow) {
  int used = graphics_text_layout_get_content_size(text, font, box, overflow,
                                                   GTextAlignmentCenter).h;
  return used > box.size.h ? box.size.h : used;
}

/** The biggest system font the text actually fits across a tile, measured rather than guessed. */
static const char *body_font(const char *text, int width) {
  static const char *const LADDER[] = {
    FONT_KEY_BITHAM_42_BOLD, FONT_KEY_BITHAM_30_BLACK, FONT_KEY_GOTHIC_28_BOLD,
    FONT_KEY_GOTHIC_24_BOLD, FONT_KEY_GOTHIC_18_BOLD,
  };
  for (unsigned int i = 0; i < ARRAY_LENGTH(LADDER); i++) {
    GSize used = graphics_text_layout_get_content_size(
        text, fonts_get_system_font(LADDER[i]), GRect(0, 0, 200, 60),
        GTextOverflowModeFill, GTextAlignmentCenter);
    if (used.w <= width) return LADDER[i];
  }
  return LADDER[ARRAY_LENGTH(LADDER) - 1];
}

static void update_quadrant(Layer *layer, GContext *ctx) {
  const Quadrant *quadrant = &s_quadrants[slot_of(*(int *)layer_get_data(layer))];
  GRect bounds = layer_get_bounds(layer);
  graphics_context_set_antialiased(ctx, true);
  graphics_context_set_text_color(ctx, ink());
  graphics_context_set_stroke_color(ctx, ink());
  graphics_context_set_fill_color(ctx, ink());

  // An empty label means the phone decided this tile has no title; the reading takes it all.
  int body_top = 4;
  if (quadrant->label[0]) {
    GRect label = GRect(2, 0, bounds.size.w - 4, TITLE_HEIGHT);
    graphics_context_set_text_color(ctx, muted());
    graphics_draw_text(ctx, quadrant->label, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD),
                       label, GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
    graphics_context_set_text_color(ctx, ink());
    body_top = TITLE_HEIGHT;
  }
  // Artwork is sized to the tile under its title, so it wants every pixel below that.
  int body_height = bounds.size.h - body_top - (quadrant->shape == 'm' ? 0 : 2);
  GRect body = GRect(4, body_top, bounds.size.w - 8, body_height);
  switch (quadrant->shape) {
    case 'r': {
      int bar_height = 14;
      int gap = 4;
      GFont font = fonts_get_system_font(body_font(quadrant->text, body.size.w));
      GRect text_box = GRect(body.origin.x, body.origin.y, body.size.w,
                             body_height - bar_height - gap);
      int used = measure(quadrant->text, font, text_box, GTextOverflowModeFill);
      int top = body.origin.y + (body_height - used - gap - bar_height) / 2;
      graphics_draw_text(ctx, quadrant->text, font,
                         GRect(text_box.origin.x, top, text_box.size.w, used),
                         GTextOverflowModeFill, GTextAlignmentCenter, NULL);
      draw_bar(ctx, GRect(6, top + used + gap, bounds.size.w - 12, bar_height), quadrant);
      break;
    }
    case 'b': {
      int size = body_height * 3 / 4;
      if (size > bounds.size.w - 16) size = bounds.size.w - 16;
      draw_checkbox(ctx, GRect((bounds.size.w - size) / 2,
                               body_top + (body_height - size) / 2, size, size),
                    quadrant->on);
      break;
    }
    case 'm':
      draw_art(ctx, GRect(0, body_top, bounds.size.w, body_height), s_art[quadrant->index],
               s_art_mono[quadrant->index]);
      break;
    default: {
      GFont font = fonts_get_system_font(body_font(quadrant->text, body.size.w));
      int used = measure(quadrant->text, font, body, GTextOverflowModeWordWrap);
      graphics_draw_text(ctx, quadrant->text, font,
                         GRect(body.origin.x, body.origin.y + (body_height - used) / 2,
                               body.size.w, used),
                         GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
      break;
    }
  }

  // Last, so a bitmap sized for a layout this tile has since changed can't paint over it.
  if (quadrant->outlined && touch_service_is_enabled()) {
    graphics_context_set_stroke_color(ctx, ACCENT);
    graphics_context_set_stroke_width(ctx, OUTLINE_STROKE);
    graphics_draw_round_rect(ctx, grect_inset(bounds, GEdgeInsets(OUTLINE_INSET)), 8);
    graphics_context_set_stroke_width(ctx, 1);
    graphics_context_set_stroke_color(ctx, ink());
  }
}

// ---------------------------------------------------------------- app

/** The largest square artwork a quadrant can show under its title, for PKJS to request. */
static void send_art_size(void) {
  int usable = s_frames[0].size.h - TITLE_HEIGHT - OUTLINE_ROOM;
  int width = s_frames[0].size.w - OUTLINE_ROOM * 2;
  int size = width < usable ? width : usable;
  if (size > ART_SIZE_MAX) size = ART_SIZE_MAX;
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_int(out, MESSAGE_KEY_artSize, &size, sizeof(int), true);
  app_message_outbox_send();
}

static void send_action(int quadrant) {
  DictionaryIterator *out;
  if (app_message_outbox_begin(&out) != APP_MSG_OK) return;
  dict_write_int(out, MESSAGE_KEY_action, &quadrant, sizeof(int), true);
  app_message_outbox_send();
}

__attribute__((unused))
static void touch_handler(const TouchEvent *event, void *context) {
  if (event->type != TouchEvent_Liftoff) return;
  for (int i = 0; i < TILES; i++) {
    const Quadrant *quadrant = &s_quadrants[slot_of(i)];
    if (!quadrant->tappable) continue;
    if (grect_contains_point(&s_frames[i], &GPoint(event->x, event->y))) {
      // An action round-trips through the phone, so acknowledge the tap immediately. A tap that
      // only moves the quadrant on to its next reading has nothing to acknowledge.
      if (quadrant->outlined) vibes_short_pulse();
      send_action(slot_of(i));
      return;
    }
  }
}

/** The last thing each quadrant drew, so a launch away from the phone isn't eight blanks. */
static void load_quadrants(void) {
  char payload[PAYLOAD_MAX];
  for (int i = 0; i < QUADRANTS; i++) {
    if (!persist_exists(PERSIST_QUAD + i)) continue;
    if (persist_read_string(PERSIST_QUAD + i, payload, sizeof(payload)) <= 0) continue;
    parse(payload, &s_quadrants[i]);
    s_quadrants[i].index = i;
  }
}

static void send_art_size(void);

static void inbox_received(DictionaryIterator *iter, void *context) {
  // PKJS is restarted whenever the phone reconnects, and comes back not knowing how big this
  // watch's tiles are.
  if (dict_find(iter, MESSAGE_KEY_hello)) {
    send_art_size();
    return;
  }
  Tuple *theme = dict_find(iter, MESSAGE_KEY_theme);
  if (theme) {
    s_dark = theme->value->int32 != 0;
    persist_write_bool(PERSIST_DARK, s_dark);
    window_set_background_color(s_window, paper());
    mark_page_dirty();
  }
  Tuple *quick = dict_find(iter, MESSAGE_KEY_quick);
  if (quick) {
    s_quick_slot = quick->value->int32;
    persist_write_int(PERSIST_QUICK, s_quick_slot);
  }
  Tuple *img_quad = dict_find(iter, MESSAGE_KEY_imgQuad);
  if (img_quad) {
    int index = img_quad->value->int32;
    Tuple *width = dict_find(iter, MESSAGE_KEY_imgWidth);
    Tuple *height = dict_find(iter, MESSAGE_KEY_imgHeight);
    Tuple *palette = dict_find(iter, MESSAGE_KEY_imgPalette);
    Tuple *pixels = dict_find(iter, MESSAGE_KEY_imgPixels);
    if (index >= 0 && index < QUADRANTS && width && height && palette && pixels) {
      set_art(index, width, height, palette, pixels);
      mark_page_dirty();
    }
    return;
  }
  for (int i = 0; i < QUADRANTS; i++) {
    Tuple *tuple = dict_find(iter, quad_key(i));
    if (!tuple) continue;
    parse(tuple->value->cstring, &s_quadrants[i]);
    s_quadrants[i].index = i;
    if (strlen(tuple->value->cstring) < PAYLOAD_MAX) {
      persist_write_string(PERSIST_QUAD + i, tuple->value->cstring);
    }
  }
  mark_page_dirty();
}

static void mark_page_dirty(void) {
  for (int i = 0; i < TILES; i++) layer_mark_dirty(s_layers[i]);
  layer_mark_dirty(s_grid);
}

/** The dividing lines that make four tiles out of one screen, and which page they are. */
static void update_grid(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);
  graphics_context_set_stroke_color(ctx, muted());
  graphics_context_set_stroke_width(ctx, 1);
  graphics_draw_line(ctx, GPoint(bounds.size.w / 2, 0), GPoint(bounds.size.w / 2, bounds.size.h));
  graphics_draw_line(ctx, GPoint(0, bounds.size.h / 2), GPoint(bounds.size.w, bounds.size.h / 2));

  // Two pips where the tiles meet: the one place on screen no tile draws in.
  GPoint centre = GPoint(bounds.size.w / 2, bounds.size.h / 2);
  for (int page = 0; page < QUADRANTS / TILES; page++) {
    GPoint pip = GPoint(centre.x + (page == 0 ? -6 : 6), centre.y);
    if (page == s_page) {
      graphics_context_set_fill_color(ctx, ACCENT);
      graphics_fill_circle(ctx, pip, 3);
    } else {
      graphics_context_set_fill_color(ctx, paper());
      graphics_fill_circle(ctx, pip, 3);
      graphics_context_set_stroke_color(ctx, muted());
      graphics_draw_circle(ctx, pip, 3);
    }
  }
  graphics_context_set_fill_color(ctx, ink());
}

static void turn_page(ClickRecognizerRef recognizer, void *context) {
  bool up = click_recognizer_get_button_id(recognizer) == BUTTON_ID_UP;
  if (!paging_out()) {
    // Opened the ordinary way: both buttons cycle the pages.
    s_page = (s_page + (up ? 1 : -1) + PAGES) % PAGES;
    mark_page_dirty();
    return;
  }
  // Opened by a quick-launch press: that button goes further in, the other one comes back out,
  // and coming back out of the first page leaves — the way the timeline behaves.
  if (up == (s_quick_slot == QUICK_UP)) {
    if (s_page + 1 < PAGES) {
      s_page++;
      mark_page_dirty();
    }
  } else if (s_page > 0) {
    s_page--;
    mark_page_dirty();
  } else {
    window_stack_pop_all(true);
  }
}

static void click_config(void *context) {
  window_single_click_subscribe(BUTTON_ID_UP, turn_page);
  window_single_click_subscribe(BUTTON_ID_DOWN, turn_page);
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  // A round display clips the corners off a 2x2 grid, so the grid is the largest square that
  // fits inside the circle — anything wider loses a tile's outer corner, label and all.
  GRect root_bounds = layer_get_bounds(root);
  GRect bounds = grect_inset(
      root_bounds, GEdgeInsets(PBL_IF_ROUND_ELSE(root_bounds.size.w * 293 / 2000, 0)));
  int half_w = bounds.size.w / 2;
  int half_h = bounds.size.h / 2;

  s_grid = layer_create(bounds);
  layer_set_update_proc(s_grid, update_grid);
  layer_add_child(root, s_grid);

  for (int i = 0; i < QUADRANTS; i++) {
    parse("t||...", &s_quadrants[i]);
    s_quadrants[i].index = i;
  }
  load_quadrants();
  for (int i = 0; i < TILES; i++) {
    GRect frame = GRect(bounds.origin.x + (i % 2) * half_w, bounds.origin.y + (i / 2) * half_h,
                        half_w, half_h);
    s_frames[i] = frame;
    // The tile holds its position; which of the eight readings it shows follows the page.
    Layer *layer = layer_create_with_data(frame, sizeof(int));
    *(int *)layer_get_data(layer) = i;
    layer_set_update_proc(layer, update_quadrant);
    layer_add_child(root, layer);
    s_layers[i] = layer;
  }
  send_art_size();
}

static void window_unload(Window *window) {
  layer_destroy(s_grid);
  for (int i = 0; i < TILES; i++) layer_destroy(s_layers[i]);
  for (int i = 0; i < QUADRANTS; i++) {
    if (s_art[i]) {
      gbitmap_destroy(s_art[i]);
      s_art[i] = NULL;
    }
  }
}

static void init(void) {
  s_dark = persist_read_bool(PERSIST_DARK);
  s_quick_slot = persist_read_int(PERSIST_QUICK);
  s_quick_launched = launch_reason() == APP_LAUNCH_QUICK_LAUNCH;
  s_window = window_create();
  window_set_background_color(s_window, paper());
  window_set_window_handlers(s_window, (WindowHandlers){
      .load = window_load,
      .unload = window_unload,
  });
  window_set_click_config_provider(s_window, click_config);
  app_message_register_inbox_received(inbox_received);
  // Artwork is 4-bpp: a 96x96 tile is 4608 bytes of pixels plus its palette.
  app_message_open(app_message_inbox_size_maximum(), 128);
  touch_service_subscribe(touch_handler, NULL);
  window_stack_push(s_window, true);
}

static void deinit(void) {
  touch_service_unsubscribe();
  window_destroy(s_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
