package coredevices.ring.ui.screens.indexfeed

internal data class EmojiOption(
    val emoji: String,
    val name: String,
    val group: String,
    val subgroup: String,
)

private const val EMOJI_DATA_0 = """
😀	grinning face	Smileys & Emotion	face-smiling
😃	grinning face with big eyes	Smileys & Emotion	face-smiling
😄	grinning face with smiling eyes	Smileys & Emotion	face-smiling
😁	beaming face with smiling eyes	Smileys & Emotion	face-smiling
😆	grinning squinting face	Smileys & Emotion	face-smiling
😅	grinning face with sweat	Smileys & Emotion	face-smiling
🤣	rolling on the floor laughing	Smileys & Emotion	face-smiling
😂	face with tears of joy	Smileys & Emotion	face-smiling
🙂	slightly smiling face	Smileys & Emotion	face-smiling
🙃	upside-down face	Smileys & Emotion	face-smiling
🫠	melting face	Smileys & Emotion	face-smiling
😉	winking face	Smileys & Emotion	face-smiling
😊	smiling face with smiling eyes	Smileys & Emotion	face-smiling
😇	smiling face with halo	Smileys & Emotion	face-smiling
🥰	smiling face with hearts	Smileys & Emotion	face-affection
😍	smiling face with heart-eyes	Smileys & Emotion	face-affection
🤩	star-struck	Smileys & Emotion	face-affection
😘	face blowing a kiss	Smileys & Emotion	face-affection
😗	kissing face	Smileys & Emotion	face-affection
☺️	smiling face	Smileys & Emotion	face-affection
😚	kissing face with closed eyes	Smileys & Emotion	face-affection
😙	kissing face with smiling eyes	Smileys & Emotion	face-affection
🥲	smiling face with tear	Smileys & Emotion	face-affection
😋	face savoring food	Smileys & Emotion	face-tongue
😛	face with tongue	Smileys & Emotion	face-tongue
😜	winking face with tongue	Smileys & Emotion	face-tongue
🤪	zany face	Smileys & Emotion	face-tongue
😝	squinting face with tongue	Smileys & Emotion	face-tongue
🤑	money-mouth face	Smileys & Emotion	face-tongue
🤗	smiling face with open hands	Smileys & Emotion	face-hand
🤭	face with hand over mouth	Smileys & Emotion	face-hand
🫢	face with open eyes and hand over mouth	Smileys & Emotion	face-hand
🫣	face with peeking eye	Smileys & Emotion	face-hand
🤫	shushing face	Smileys & Emotion	face-hand
🤔	thinking face	Smileys & Emotion	face-hand
🫡	saluting face	Smileys & Emotion	face-hand
🤐	zipper-mouth face	Smileys & Emotion	face-neutral-skeptical
🤨	face with raised eyebrow	Smileys & Emotion	face-neutral-skeptical
😐	neutral face	Smileys & Emotion	face-neutral-skeptical
😑	expressionless face	Smileys & Emotion	face-neutral-skeptical
😶	face without mouth	Smileys & Emotion	face-neutral-skeptical
🫥	dotted line face	Smileys & Emotion	face-neutral-skeptical
😶‍🌫️	face in clouds	Smileys & Emotion	face-neutral-skeptical
😏	smirking face	Smileys & Emotion	face-neutral-skeptical
😒	unamused face	Smileys & Emotion	face-neutral-skeptical
🙄	face with rolling eyes	Smileys & Emotion	face-neutral-skeptical
😬	grimacing face	Smileys & Emotion	face-neutral-skeptical
😮‍💨	face exhaling	Smileys & Emotion	face-neutral-skeptical
🤥	lying face	Smileys & Emotion	face-neutral-skeptical
🫨	shaking face	Smileys & Emotion	face-neutral-skeptical
🙂‍↔️	head shaking horizontally	Smileys & Emotion	face-neutral-skeptical
🙂‍↕️	head shaking vertically	Smileys & Emotion	face-neutral-skeptical
😌	relieved face	Smileys & Emotion	face-sleepy
😔	pensive face	Smileys & Emotion	face-sleepy
😪	sleepy face	Smileys & Emotion	face-sleepy
🤤	drooling face	Smileys & Emotion	face-sleepy
😴	sleeping face	Smileys & Emotion	face-sleepy
🫩	face with bags under eyes	Smileys & Emotion	face-sleepy
😷	face with medical mask	Smileys & Emotion	face-unwell
🤒	face with thermometer	Smileys & Emotion	face-unwell
🤕	face with head-bandage	Smileys & Emotion	face-unwell
🤢	nauseated face	Smileys & Emotion	face-unwell
🤮	face vomiting	Smileys & Emotion	face-unwell
🤧	sneezing face	Smileys & Emotion	face-unwell
🥵	hot face	Smileys & Emotion	face-unwell
🥶	cold face	Smileys & Emotion	face-unwell
🥴	woozy face	Smileys & Emotion	face-unwell
😵	face with crossed-out eyes	Smileys & Emotion	face-unwell
😵‍💫	face with spiral eyes	Smileys & Emotion	face-unwell
🤯	exploding head	Smileys & Emotion	face-unwell
🤠	cowboy hat face	Smileys & Emotion	face-hat
🥳	partying face	Smileys & Emotion	face-hat
🥸	disguised face	Smileys & Emotion	face-hat
😎	smiling face with sunglasses	Smileys & Emotion	face-glasses
🤓	nerd face	Smileys & Emotion	face-glasses
🧐	face with monocle	Smileys & Emotion	face-glasses
😕	confused face	Smileys & Emotion	face-concerned
🫤	face with diagonal mouth	Smileys & Emotion	face-concerned
😟	worried face	Smileys & Emotion	face-concerned
🙁	slightly frowning face	Smileys & Emotion	face-concerned
☹️	frowning face	Smileys & Emotion	face-concerned
😮	face with open mouth	Smileys & Emotion	face-concerned
😯	hushed face	Smileys & Emotion	face-concerned
😲	astonished face	Smileys & Emotion	face-concerned
😳	flushed face	Smileys & Emotion	face-concerned
🫪	distorted face	Smileys & Emotion	face-concerned
🥺	pleading face	Smileys & Emotion	face-concerned
🥹	face holding back tears	Smileys & Emotion	face-concerned
😦	frowning face with open mouth	Smileys & Emotion	face-concerned
😧	anguished face	Smileys & Emotion	face-concerned
😨	fearful face	Smileys & Emotion	face-concerned
😰	anxious face with sweat	Smileys & Emotion	face-concerned
😥	sad but relieved face	Smileys & Emotion	face-concerned
😢	crying face	Smileys & Emotion	face-concerned
😭	loudly crying face	Smileys & Emotion	face-concerned
😱	face screaming in fear	Smileys & Emotion	face-concerned
😖	confounded face	Smileys & Emotion	face-concerned
😣	persevering face	Smileys & Emotion	face-concerned
😞	disappointed face	Smileys & Emotion	face-concerned
😓	downcast face with sweat	Smileys & Emotion	face-concerned
😩	weary face	Smileys & Emotion	face-concerned
😫	tired face	Smileys & Emotion	face-concerned
🥱	yawning face	Smileys & Emotion	face-concerned
😤	face with steam from nose	Smileys & Emotion	face-negative
😡	enraged face	Smileys & Emotion	face-negative
😠	angry face	Smileys & Emotion	face-negative
🤬	face with symbols on mouth	Smileys & Emotion	face-negative
😈	smiling face with horns	Smileys & Emotion	face-negative
👿	angry face with horns	Smileys & Emotion	face-negative
💀	skull	Smileys & Emotion	face-negative
☠️	skull and crossbones	Smileys & Emotion	face-negative
💩	pile of poo	Smileys & Emotion	face-costume
🤡	clown face	Smileys & Emotion	face-costume
👹	ogre	Smileys & Emotion	face-costume
👺	goblin	Smileys & Emotion	face-costume
👻	ghost	Smileys & Emotion	face-costume
👽	alien	Smileys & Emotion	face-costume
👾	alien monster	Smileys & Emotion	face-costume
🤖	robot	Smileys & Emotion	face-costume
😺	grinning cat	Smileys & Emotion	cat-face
😸	grinning cat with smiling eyes	Smileys & Emotion	cat-face
😹	cat with tears of joy	Smileys & Emotion	cat-face
😻	smiling cat with heart-eyes	Smileys & Emotion	cat-face
😼	cat with wry smile	Smileys & Emotion	cat-face
😽	kissing cat	Smileys & Emotion	cat-face
🙀	weary cat	Smileys & Emotion	cat-face
😿	crying cat	Smileys & Emotion	cat-face
😾	pouting cat	Smileys & Emotion	cat-face
🙈	see-no-evil monkey	Smileys & Emotion	monkey-face
🙉	hear-no-evil monkey	Smileys & Emotion	monkey-face
🙊	speak-no-evil monkey	Smileys & Emotion	monkey-face
💌	love letter	Smileys & Emotion	heart
💘	heart with arrow	Smileys & Emotion	heart
💝	heart with ribbon	Smileys & Emotion	heart
💖	sparkling heart	Smileys & Emotion	heart
💗	growing heart	Smileys & Emotion	heart
💓	beating heart	Smileys & Emotion	heart
💞	revolving hearts	Smileys & Emotion	heart
💕	two hearts	Smileys & Emotion	heart
💟	heart decoration	Smileys & Emotion	heart
❣️	heart exclamation	Smileys & Emotion	heart
💔	broken heart	Smileys & Emotion	heart
❤️‍🔥	heart on fire	Smileys & Emotion	heart
❤️‍🩹	mending heart	Smileys & Emotion	heart
❤️	red heart	Smileys & Emotion	heart
🩷	pink heart	Smileys & Emotion	heart
🧡	orange heart	Smileys & Emotion	heart
💛	yellow heart	Smileys & Emotion	heart
💚	green heart	Smileys & Emotion	heart
💙	blue heart	Smileys & Emotion	heart
🩵	light blue heart	Smileys & Emotion	heart
💜	purple heart	Smileys & Emotion	heart
🤎	brown heart	Smileys & Emotion	heart
🖤	black heart	Smileys & Emotion	heart
🩶	grey heart	Smileys & Emotion	heart
🤍	white heart	Smileys & Emotion	heart
💋	kiss mark	Smileys & Emotion	emotion
💯	hundred points	Smileys & Emotion	emotion
💢	anger symbol	Smileys & Emotion	emotion
🫯	fight cloud	Smileys & Emotion	emotion
💥	collision	Smileys & Emotion	emotion
💫	dizzy	Smileys & Emotion	emotion
💦	sweat droplets	Smileys & Emotion	emotion
💨	dashing away	Smileys & Emotion	emotion
🕳️	hole	Smileys & Emotion	emotion
💬	speech balloon	Smileys & Emotion	emotion
👁️‍🗨️	eye in speech bubble	Smileys & Emotion	emotion
🗨️	left speech bubble	Smileys & Emotion	emotion
🗯️	right anger bubble	Smileys & Emotion	emotion
💭	thought balloon	Smileys & Emotion	emotion
💤	ZZZ	Smileys & Emotion	emotion
👋	waving hand	People & Body	hand-fingers-open
👋🏻	waving hand: light skin tone	People & Body	hand-fingers-open
👋🏼	waving hand: medium-light skin tone	People & Body	hand-fingers-open
👋🏽	waving hand: medium skin tone	People & Body	hand-fingers-open
👋🏾	waving hand: medium-dark skin tone	People & Body	hand-fingers-open
👋🏿	waving hand: dark skin tone	People & Body	hand-fingers-open
🤚	raised back of hand	People & Body	hand-fingers-open
🤚🏻	raised back of hand: light skin tone	People & Body	hand-fingers-open
🤚🏼	raised back of hand: medium-light skin tone	People & Body	hand-fingers-open
🤚🏽	raised back of hand: medium skin tone	People & Body	hand-fingers-open
🤚🏾	raised back of hand: medium-dark skin tone	People & Body	hand-fingers-open
🤚🏿	raised back of hand: dark skin tone	People & Body	hand-fingers-open
🖐️	hand with fingers splayed	People & Body	hand-fingers-open
🖐🏻	hand with fingers splayed: light skin tone	People & Body	hand-fingers-open
🖐🏼	hand with fingers splayed: medium-light skin tone	People & Body	hand-fingers-open
🖐🏽	hand with fingers splayed: medium skin tone	People & Body	hand-fingers-open
🖐🏾	hand with fingers splayed: medium-dark skin tone	People & Body	hand-fingers-open
🖐🏿	hand with fingers splayed: dark skin tone	People & Body	hand-fingers-open
✋	raised hand	People & Body	hand-fingers-open
✋🏻	raised hand: light skin tone	People & Body	hand-fingers-open
✋🏼	raised hand: medium-light skin tone	People & Body	hand-fingers-open
✋🏽	raised hand: medium skin tone	People & Body	hand-fingers-open
✋🏾	raised hand: medium-dark skin tone	People & Body	hand-fingers-open
✋🏿	raised hand: dark skin tone	People & Body	hand-fingers-open
🖖	vulcan salute	People & Body	hand-fingers-open
🖖🏻	vulcan salute: light skin tone	People & Body	hand-fingers-open
🖖🏼	vulcan salute: medium-light skin tone	People & Body	hand-fingers-open
🖖🏽	vulcan salute: medium skin tone	People & Body	hand-fingers-open
🖖🏾	vulcan salute: medium-dark skin tone	People & Body	hand-fingers-open
🖖🏿	vulcan salute: dark skin tone	People & Body	hand-fingers-open
🫱	rightwards hand	People & Body	hand-fingers-open
🫱🏻	rightwards hand: light skin tone	People & Body	hand-fingers-open
🫱🏼	rightwards hand: medium-light skin tone	People & Body	hand-fingers-open
🫱🏽	rightwards hand: medium skin tone	People & Body	hand-fingers-open
🫱🏾	rightwards hand: medium-dark skin tone	People & Body	hand-fingers-open
🫱🏿	rightwards hand: dark skin tone	People & Body	hand-fingers-open
🫲	leftwards hand	People & Body	hand-fingers-open
🫲🏻	leftwards hand: light skin tone	People & Body	hand-fingers-open
🫲🏼	leftwards hand: medium-light skin tone	People & Body	hand-fingers-open
🫲🏽	leftwards hand: medium skin tone	People & Body	hand-fingers-open
🫲🏾	leftwards hand: medium-dark skin tone	People & Body	hand-fingers-open
🫲🏿	leftwards hand: dark skin tone	People & Body	hand-fingers-open
🫳	palm down hand	People & Body	hand-fingers-open
🫳🏻	palm down hand: light skin tone	People & Body	hand-fingers-open
🫳🏼	palm down hand: medium-light skin tone	People & Body	hand-fingers-open
🫳🏽	palm down hand: medium skin tone	People & Body	hand-fingers-open
🫳🏾	palm down hand: medium-dark skin tone	People & Body	hand-fingers-open
🫳🏿	palm down hand: dark skin tone	People & Body	hand-fingers-open
🫴	palm up hand	People & Body	hand-fingers-open
🫴🏻	palm up hand: light skin tone	People & Body	hand-fingers-open
🫴🏼	palm up hand: medium-light skin tone	People & Body	hand-fingers-open
🫴🏽	palm up hand: medium skin tone	People & Body	hand-fingers-open
🫴🏾	palm up hand: medium-dark skin tone	People & Body	hand-fingers-open
🫴🏿	palm up hand: dark skin tone	People & Body	hand-fingers-open
🫷	leftwards pushing hand	People & Body	hand-fingers-open
🫷🏻	leftwards pushing hand: light skin tone	People & Body	hand-fingers-open
🫷🏼	leftwards pushing hand: medium-light skin tone	People & Body	hand-fingers-open
🫷🏽	leftwards pushing hand: medium skin tone	People & Body	hand-fingers-open
🫷🏾	leftwards pushing hand: medium-dark skin tone	People & Body	hand-fingers-open
🫷🏿	leftwards pushing hand: dark skin tone	People & Body	hand-fingers-open
🫸	rightwards pushing hand	People & Body	hand-fingers-open
🫸🏻	rightwards pushing hand: light skin tone	People & Body	hand-fingers-open
🫸🏼	rightwards pushing hand: medium-light skin tone	People & Body	hand-fingers-open
🫸🏽	rightwards pushing hand: medium skin tone	People & Body	hand-fingers-open
🫸🏾	rightwards pushing hand: medium-dark skin tone	People & Body	hand-fingers-open
🫸🏿	rightwards pushing hand: dark skin tone	People & Body	hand-fingers-open
👌	OK hand	People & Body	hand-fingers-partial
👌🏻	OK hand: light skin tone	People & Body	hand-fingers-partial
👌🏼	OK hand: medium-light skin tone	People & Body	hand-fingers-partial
👌🏽	OK hand: medium skin tone	People & Body	hand-fingers-partial
👌🏾	OK hand: medium-dark skin tone	People & Body	hand-fingers-partial
👌🏿	OK hand: dark skin tone	People & Body	hand-fingers-partial
🤌	pinched fingers	People & Body	hand-fingers-partial
🤌🏻	pinched fingers: light skin tone	People & Body	hand-fingers-partial
🤌🏼	pinched fingers: medium-light skin tone	People & Body	hand-fingers-partial
🤌🏽	pinched fingers: medium skin tone	People & Body	hand-fingers-partial
🤌🏾	pinched fingers: medium-dark skin tone	People & Body	hand-fingers-partial
🤌🏿	pinched fingers: dark skin tone	People & Body	hand-fingers-partial
🤏	pinching hand	People & Body	hand-fingers-partial
🤏🏻	pinching hand: light skin tone	People & Body	hand-fingers-partial
🤏🏼	pinching hand: medium-light skin tone	People & Body	hand-fingers-partial
🤏🏽	pinching hand: medium skin tone	People & Body	hand-fingers-partial
🤏🏾	pinching hand: medium-dark skin tone	People & Body	hand-fingers-partial
🤏🏿	pinching hand: dark skin tone	People & Body	hand-fingers-partial
✌️	victory hand	People & Body	hand-fingers-partial
✌🏻	victory hand: light skin tone	People & Body	hand-fingers-partial
✌🏼	victory hand: medium-light skin tone	People & Body	hand-fingers-partial
✌🏽	victory hand: medium skin tone	People & Body	hand-fingers-partial
✌🏾	victory hand: medium-dark skin tone	People & Body	hand-fingers-partial
✌🏿	victory hand: dark skin tone	People & Body	hand-fingers-partial
🤞	crossed fingers	People & Body	hand-fingers-partial
🤞🏻	crossed fingers: light skin tone	People & Body	hand-fingers-partial
🤞🏼	crossed fingers: medium-light skin tone	People & Body	hand-fingers-partial
🤞🏽	crossed fingers: medium skin tone	People & Body	hand-fingers-partial
🤞🏾	crossed fingers: medium-dark skin tone	People & Body	hand-fingers-partial
🤞🏿	crossed fingers: dark skin tone	People & Body	hand-fingers-partial
🫰	hand with index finger and thumb crossed	People & Body	hand-fingers-partial
🫰🏻	hand with index finger and thumb crossed: light skin tone	People & Body	hand-fingers-partial
🫰🏼	hand with index finger and thumb crossed: medium-light skin tone	People & Body	hand-fingers-partial
🫰🏽	hand with index finger and thumb crossed: medium skin tone	People & Body	hand-fingers-partial
🫰🏾	hand with index finger and thumb crossed: medium-dark skin tone	People & Body	hand-fingers-partial
🫰🏿	hand with index finger and thumb crossed: dark skin tone	People & Body	hand-fingers-partial
🤟	love-you gesture	People & Body	hand-fingers-partial
🤟🏻	love-you gesture: light skin tone	People & Body	hand-fingers-partial
🤟🏼	love-you gesture: medium-light skin tone	People & Body	hand-fingers-partial
🤟🏽	love-you gesture: medium skin tone	People & Body	hand-fingers-partial
🤟🏾	love-you gesture: medium-dark skin tone	People & Body	hand-fingers-partial
🤟🏿	love-you gesture: dark skin tone	People & Body	hand-fingers-partial
🤘	sign of the horns	People & Body	hand-fingers-partial
🤘🏻	sign of the horns: light skin tone	People & Body	hand-fingers-partial
🤘🏼	sign of the horns: medium-light skin tone	People & Body	hand-fingers-partial
🤘🏽	sign of the horns: medium skin tone	People & Body	hand-fingers-partial
🤘🏾	sign of the horns: medium-dark skin tone	People & Body	hand-fingers-partial
🤘🏿	sign of the horns: dark skin tone	People & Body	hand-fingers-partial
🤙	call me hand	People & Body	hand-fingers-partial
🤙🏻	call me hand: light skin tone	People & Body	hand-fingers-partial
🤙🏼	call me hand: medium-light skin tone	People & Body	hand-fingers-partial
🤙🏽	call me hand: medium skin tone	People & Body	hand-fingers-partial
🤙🏾	call me hand: medium-dark skin tone	People & Body	hand-fingers-partial
🤙🏿	call me hand: dark skin tone	People & Body	hand-fingers-partial
👈	backhand index pointing left	People & Body	hand-single-finger
👈🏻	backhand index pointing left: light skin tone	People & Body	hand-single-finger
👈🏼	backhand index pointing left: medium-light skin tone	People & Body	hand-single-finger
👈🏽	backhand index pointing left: medium skin tone	People & Body	hand-single-finger
👈🏾	backhand index pointing left: medium-dark skin tone	People & Body	hand-single-finger
👈🏿	backhand index pointing left: dark skin tone	People & Body	hand-single-finger
👉	backhand index pointing right	People & Body	hand-single-finger
👉🏻	backhand index pointing right: light skin tone	People & Body	hand-single-finger
👉🏼	backhand index pointing right: medium-light skin tone	People & Body	hand-single-finger
👉🏽	backhand index pointing right: medium skin tone	People & Body	hand-single-finger
👉🏾	backhand index pointing right: medium-dark skin tone	People & Body	hand-single-finger
👉🏿	backhand index pointing right: dark skin tone	People & Body	hand-single-finger
👆	backhand index pointing up	People & Body	hand-single-finger
👆🏻	backhand index pointing up: light skin tone	People & Body	hand-single-finger
👆🏼	backhand index pointing up: medium-light skin tone	People & Body	hand-single-finger
👆🏽	backhand index pointing up: medium skin tone	People & Body	hand-single-finger
👆🏾	backhand index pointing up: medium-dark skin tone	People & Body	hand-single-finger
👆🏿	backhand index pointing up: dark skin tone	People & Body	hand-single-finger
🖕	middle finger	People & Body	hand-single-finger
🖕🏻	middle finger: light skin tone	People & Body	hand-single-finger
🖕🏼	middle finger: medium-light skin tone	People & Body	hand-single-finger
🖕🏽	middle finger: medium skin tone	People & Body	hand-single-finger
🖕🏾	middle finger: medium-dark skin tone	People & Body	hand-single-finger
🖕🏿	middle finger: dark skin tone	People & Body	hand-single-finger
👇	backhand index pointing down	People & Body	hand-single-finger
👇🏻	backhand index pointing down: light skin tone	People & Body	hand-single-finger
👇🏼	backhand index pointing down: medium-light skin tone	People & Body	hand-single-finger
👇🏽	backhand index pointing down: medium skin tone	People & Body	hand-single-finger
👇🏾	backhand index pointing down: medium-dark skin tone	People & Body	hand-single-finger
👇🏿	backhand index pointing down: dark skin tone	People & Body	hand-single-finger
☝️	index pointing up	People & Body	hand-single-finger
☝🏻	index pointing up: light skin tone	People & Body	hand-single-finger
☝🏼	index pointing up: medium-light skin tone	People & Body	hand-single-finger
☝🏽	index pointing up: medium skin tone	People & Body	hand-single-finger
☝🏾	index pointing up: medium-dark skin tone	People & Body	hand-single-finger
☝🏿	index pointing up: dark skin tone	People & Body	hand-single-finger
🫵	index pointing at the viewer	People & Body	hand-single-finger
🫵🏻	index pointing at the viewer: light skin tone	People & Body	hand-single-finger
🫵🏼	index pointing at the viewer: medium-light skin tone	People & Body	hand-single-finger
🫵🏽	index pointing at the viewer: medium skin tone	People & Body	hand-single-finger
🫵🏾	index pointing at the viewer: medium-dark skin tone	People & Body	hand-single-finger
🫵🏿	index pointing at the viewer: dark skin tone	People & Body	hand-single-finger
👍	thumbs up	People & Body	hand-fingers-closed
👍🏻	thumbs up: light skin tone	People & Body	hand-fingers-closed
👍🏼	thumbs up: medium-light skin tone	People & Body	hand-fingers-closed
👍🏽	thumbs up: medium skin tone	People & Body	hand-fingers-closed
👍🏾	thumbs up: medium-dark skin tone	People & Body	hand-fingers-closed
👍🏿	thumbs up: dark skin tone	People & Body	hand-fingers-closed
👎	thumbs down	People & Body	hand-fingers-closed
👎🏻	thumbs down: light skin tone	People & Body	hand-fingers-closed
👎🏼	thumbs down: medium-light skin tone	People & Body	hand-fingers-closed
👎🏽	thumbs down: medium skin tone	People & Body	hand-fingers-closed
👎🏾	thumbs down: medium-dark skin tone	People & Body	hand-fingers-closed
👎🏿	thumbs down: dark skin tone	People & Body	hand-fingers-closed
✊	raised fist	People & Body	hand-fingers-closed
✊🏻	raised fist: light skin tone	People & Body	hand-fingers-closed
✊🏼	raised fist: medium-light skin tone	People & Body	hand-fingers-closed
✊🏽	raised fist: medium skin tone	People & Body	hand-fingers-closed
✊🏾	raised fist: medium-dark skin tone	People & Body	hand-fingers-closed
✊🏿	raised fist: dark skin tone	People & Body	hand-fingers-closed
👊	oncoming fist	People & Body	hand-fingers-closed
👊🏻	oncoming fist: light skin tone	People & Body	hand-fingers-closed
👊🏼	oncoming fist: medium-light skin tone	People & Body	hand-fingers-closed
👊🏽	oncoming fist: medium skin tone	People & Body	hand-fingers-closed
👊🏾	oncoming fist: medium-dark skin tone	People & Body	hand-fingers-closed
👊🏿	oncoming fist: dark skin tone	People & Body	hand-fingers-closed
🤛	left-facing fist	People & Body	hand-fingers-closed
🤛🏻	left-facing fist: light skin tone	People & Body	hand-fingers-closed
🤛🏼	left-facing fist: medium-light skin tone	People & Body	hand-fingers-closed
🤛🏽	left-facing fist: medium skin tone	People & Body	hand-fingers-closed
🤛🏾	left-facing fist: medium-dark skin tone	People & Body	hand-fingers-closed
🤛🏿	left-facing fist: dark skin tone	People & Body	hand-fingers-closed
🤜	right-facing fist	People & Body	hand-fingers-closed
🤜🏻	right-facing fist: light skin tone	People & Body	hand-fingers-closed
🤜🏼	right-facing fist: medium-light skin tone	People & Body	hand-fingers-closed
🤜🏽	right-facing fist: medium skin tone	People & Body	hand-fingers-closed
🤜🏾	right-facing fist: medium-dark skin tone	People & Body	hand-fingers-closed
🤜🏿	right-facing fist: dark skin tone	People & Body	hand-fingers-closed
👏	clapping hands	People & Body	hands
👏🏻	clapping hands: light skin tone	People & Body	hands
👏🏼	clapping hands: medium-light skin tone	People & Body	hands
👏🏽	clapping hands: medium skin tone	People & Body	hands
👏🏾	clapping hands: medium-dark skin tone	People & Body	hands
👏🏿	clapping hands: dark skin tone	People & Body	hands
🙌	raising hands	People & Body	hands
🙌🏻	raising hands: light skin tone	People & Body	hands
🙌🏼	raising hands: medium-light skin tone	People & Body	hands
🙌🏽	raising hands: medium skin tone	People & Body	hands
🙌🏾	raising hands: medium-dark skin tone	People & Body	hands
🙌🏿	raising hands: dark skin tone	People & Body	hands
🫶	heart hands	People & Body	hands
🫶🏻	heart hands: light skin tone	People & Body	hands
🫶🏼	heart hands: medium-light skin tone	People & Body	hands
🫶🏽	heart hands: medium skin tone	People & Body	hands
🫶🏾	heart hands: medium-dark skin tone	People & Body	hands
🫶🏿	heart hands: dark skin tone	People & Body	hands
👐	open hands	People & Body	hands
👐🏻	open hands: light skin tone	People & Body	hands
👐🏼	open hands: medium-light skin tone	People & Body	hands
👐🏽	open hands: medium skin tone	People & Body	hands
👐🏾	open hands: medium-dark skin tone	People & Body	hands
👐🏿	open hands: dark skin tone	People & Body	hands
🤲	palms up together	People & Body	hands
🤲🏻	palms up together: light skin tone	People & Body	hands
🤲🏼	palms up together: medium-light skin tone	People & Body	hands
🤲🏽	palms up together: medium skin tone	People & Body	hands
🤲🏾	palms up together: medium-dark skin tone	People & Body	hands
🤲🏿	palms up together: dark skin tone	People & Body	hands
🤝	handshake	People & Body	hands
🤝🏻	handshake: light skin tone	People & Body	hands
🤝🏼	handshake: medium-light skin tone	People & Body	hands
🤝🏽	handshake: medium skin tone	People & Body	hands
🤝🏾	handshake: medium-dark skin tone	People & Body	hands
🤝🏿	handshake: dark skin tone	People & Body	hands
🫱🏻‍🫲🏼	handshake: light skin tone, medium-light skin tone	People & Body	hands
🫱🏻‍🫲🏽	handshake: light skin tone, medium skin tone	People & Body	hands
🫱🏻‍🫲🏾	handshake: light skin tone, medium-dark skin tone	People & Body	hands
🫱🏻‍🫲🏿	handshake: light skin tone, dark skin tone	People & Body	hands
🫱🏼‍🫲🏻	handshake: medium-light skin tone, light skin tone	People & Body	hands
🫱🏼‍🫲🏽	handshake: medium-light skin tone, medium skin tone	People & Body	hands
🫱🏼‍🫲🏾	handshake: medium-light skin tone, medium-dark skin tone	People & Body	hands
🫱🏼‍🫲🏿	handshake: medium-light skin tone, dark skin tone	People & Body	hands
🫱🏽‍🫲🏻	handshake: medium skin tone, light skin tone	People & Body	hands
🫱🏽‍🫲🏼	handshake: medium skin tone, medium-light skin tone	People & Body	hands
🫱🏽‍🫲🏾	handshake: medium skin tone, medium-dark skin tone	People & Body	hands
🫱🏽‍🫲🏿	handshake: medium skin tone, dark skin tone	People & Body	hands
🫱🏾‍🫲🏻	handshake: medium-dark skin tone, light skin tone	People & Body	hands
🫱🏾‍🫲🏼	handshake: medium-dark skin tone, medium-light skin tone	People & Body	hands
🫱🏾‍🫲🏽	handshake: medium-dark skin tone, medium skin tone	People & Body	hands
🫱🏾‍🫲🏿	handshake: medium-dark skin tone, dark skin tone	People & Body	hands
🫱🏿‍🫲🏻	handshake: dark skin tone, light skin tone	People & Body	hands
🫱🏿‍🫲🏼	handshake: dark skin tone, medium-light skin tone	People & Body	hands
🫱🏿‍🫲🏽	handshake: dark skin tone, medium skin tone	People & Body	hands
🫱🏿‍🫲🏾	handshake: dark skin tone, medium-dark skin tone	People & Body	hands
🙏	folded hands	People & Body	hands
🙏🏻	folded hands: light skin tone	People & Body	hands
🙏🏼	folded hands: medium-light skin tone	People & Body	hands
🙏🏽	folded hands: medium skin tone	People & Body	hands
🙏🏾	folded hands: medium-dark skin tone	People & Body	hands
🙏🏿	folded hands: dark skin tone	People & Body	hands
✍️	writing hand	People & Body	hand-prop
✍🏻	writing hand: light skin tone	People & Body	hand-prop
✍🏼	writing hand: medium-light skin tone	People & Body	hand-prop
✍🏽	writing hand: medium skin tone	People & Body	hand-prop
✍🏾	writing hand: medium-dark skin tone	People & Body	hand-prop
✍🏿	writing hand: dark skin tone	People & Body	hand-prop
💅	nail polish	People & Body	hand-prop
💅🏻	nail polish: light skin tone	People & Body	hand-prop
💅🏼	nail polish: medium-light skin tone	People & Body	hand-prop
💅🏽	nail polish: medium skin tone	People & Body	hand-prop
💅🏾	nail polish: medium-dark skin tone	People & Body	hand-prop
💅🏿	nail polish: dark skin tone	People & Body	hand-prop
🤳	selfie	People & Body	hand-prop
🤳🏻	selfie: light skin tone	People & Body	hand-prop
🤳🏼	selfie: medium-light skin tone	People & Body	hand-prop
🤳🏽	selfie: medium skin tone	People & Body	hand-prop
🤳🏾	selfie: medium-dark skin tone	People & Body	hand-prop
🤳🏿	selfie: dark skin tone	People & Body	hand-prop
💪	flexed biceps	People & Body	body-parts
💪🏻	flexed biceps: light skin tone	People & Body	body-parts
💪🏼	flexed biceps: medium-light skin tone	People & Body	body-parts
💪🏽	flexed biceps: medium skin tone	People & Body	body-parts
💪🏾	flexed biceps: medium-dark skin tone	People & Body	body-parts
💪🏿	flexed biceps: dark skin tone	People & Body	body-parts
🦾	mechanical arm	People & Body	body-parts
🦿	mechanical leg	People & Body	body-parts
🦵	leg	People & Body	body-parts
🦵🏻	leg: light skin tone	People & Body	body-parts
🦵🏼	leg: medium-light skin tone	People & Body	body-parts
🦵🏽	leg: medium skin tone	People & Body	body-parts
🦵🏾	leg: medium-dark skin tone	People & Body	body-parts
🦵🏿	leg: dark skin tone	People & Body	body-parts
🦶	foot	People & Body	body-parts
🦶🏻	foot: light skin tone	People & Body	body-parts
🦶🏼	foot: medium-light skin tone	People & Body	body-parts
🦶🏽	foot: medium skin tone	People & Body	body-parts
🦶🏾	foot: medium-dark skin tone	People & Body	body-parts
🦶🏿	foot: dark skin tone	People & Body	body-parts
👂	ear	People & Body	body-parts
👂🏻	ear: light skin tone	People & Body	body-parts
👂🏼	ear: medium-light skin tone	People & Body	body-parts
👂🏽	ear: medium skin tone	People & Body	body-parts
👂🏾	ear: medium-dark skin tone	People & Body	body-parts
👂🏿	ear: dark skin tone	People & Body	body-parts
🦻	ear with hearing aid	People & Body	body-parts
🦻🏻	ear with hearing aid: light skin tone	People & Body	body-parts
🦻🏼	ear with hearing aid: medium-light skin tone	People & Body	body-parts
🦻🏽	ear with hearing aid: medium skin tone	People & Body	body-parts
🦻🏾	ear with hearing aid: medium-dark skin tone	People & Body	body-parts
🦻🏿	ear with hearing aid: dark skin tone	People & Body	body-parts
👃	nose	People & Body	body-parts
👃🏻	nose: light skin tone	People & Body	body-parts
👃🏼	nose: medium-light skin tone	People & Body	body-parts
👃🏽	nose: medium skin tone	People & Body	body-parts
👃🏾	nose: medium-dark skin tone	People & Body	body-parts
👃🏿	nose: dark skin tone	People & Body	body-parts
🧠	brain	People & Body	body-parts
🫀	anatomical heart	People & Body	body-parts
🫁	lungs	People & Body	body-parts
🦷	tooth	People & Body	body-parts
🦴	bone	People & Body	body-parts
👀	eyes	People & Body	body-parts
👁️	eye	People & Body	body-parts
👅	tongue	People & Body	body-parts
👄	mouth	People & Body	body-parts
🫦	biting lip	People & Body	body-parts
👶	baby	People & Body	person
👶🏻	baby: light skin tone	People & Body	person
👶🏼	baby: medium-light skin tone	People & Body	person
👶🏽	baby: medium skin tone	People & Body	person
👶🏾	baby: medium-dark skin tone	People & Body	person
👶🏿	baby: dark skin tone	People & Body	person
🧒	child	People & Body	person
🧒🏻	child: light skin tone	People & Body	person
🧒🏼	child: medium-light skin tone	People & Body	person
🧒🏽	child: medium skin tone	People & Body	person
🧒🏾	child: medium-dark skin tone	People & Body	person
🧒🏿	child: dark skin tone	People & Body	person
👦	boy	People & Body	person
👦🏻	boy: light skin tone	People & Body	person
👦🏼	boy: medium-light skin tone	People & Body	person
👦🏽	boy: medium skin tone	People & Body	person
👦🏾	boy: medium-dark skin tone	People & Body	person
👦🏿	boy: dark skin tone	People & Body	person
👧	girl	People & Body	person
👧🏻	girl: light skin tone	People & Body	person
👧🏼	girl: medium-light skin tone	People & Body	person
👧🏽	girl: medium skin tone	People & Body	person
👧🏾	girl: medium-dark skin tone	People & Body	person
👧🏿	girl: dark skin tone	People & Body	person
🧑	person	People & Body	person
🧑🏻	person: light skin tone	People & Body	person
🧑🏼	person: medium-light skin tone	People & Body	person
🧑🏽	person: medium skin tone	People & Body	person
🧑🏾	person: medium-dark skin tone	People & Body	person
🧑🏿	person: dark skin tone	People & Body	person
👱	person: blond hair	People & Body	person
👱🏻	person: light skin tone, blond hair	People & Body	person
👱🏼	person: medium-light skin tone, blond hair	People & Body	person
👱🏽	person: medium skin tone, blond hair	People & Body	person
👱🏾	person: medium-dark skin tone, blond hair	People & Body	person
👱🏿	person: dark skin tone, blond hair	People & Body	person
👨	man	People & Body	person
👨🏻	man: light skin tone	People & Body	person
👨🏼	man: medium-light skin tone	People & Body	person
👨🏽	man: medium skin tone	People & Body	person
👨🏾	man: medium-dark skin tone	People & Body	person
👨🏿	man: dark skin tone	People & Body	person
🧔	person: beard	People & Body	person
🧔🏻	person: light skin tone, beard	People & Body	person
🧔🏼	person: medium-light skin tone, beard	People & Body	person
🧔🏽	person: medium skin tone, beard	People & Body	person
🧔🏾	person: medium-dark skin tone, beard	People & Body	person
🧔🏿	person: dark skin tone, beard	People & Body	person
🧔‍♂️	man: beard	People & Body	person
🧔🏻‍♂️	man: light skin tone, beard	People & Body	person
🧔🏼‍♂️	man: medium-light skin tone, beard	People & Body	person
🧔🏽‍♂️	man: medium skin tone, beard	People & Body	person
🧔🏾‍♂️	man: medium-dark skin tone, beard	People & Body	person
🧔🏿‍♂️	man: dark skin tone, beard	People & Body	person
🧔‍♀️	woman: beard	People & Body	person
🧔🏻‍♀️	woman: light skin tone, beard	People & Body	person
🧔🏼‍♀️	woman: medium-light skin tone, beard	People & Body	person
🧔🏽‍♀️	woman: medium skin tone, beard	People & Body	person
🧔🏾‍♀️	woman: medium-dark skin tone, beard	People & Body	person
🧔🏿‍♀️	woman: dark skin tone, beard	People & Body	person
👨‍🦰	man: red hair	People & Body	person
👨🏻‍🦰	man: light skin tone, red hair	People & Body	person
👨🏼‍🦰	man: medium-light skin tone, red hair	People & Body	person
👨🏽‍🦰	man: medium skin tone, red hair	People & Body	person
👨🏾‍🦰	man: medium-dark skin tone, red hair	People & Body	person
👨🏿‍🦰	man: dark skin tone, red hair	People & Body	person
👨‍🦱	man: curly hair	People & Body	person
👨🏻‍🦱	man: light skin tone, curly hair	People & Body	person
👨🏼‍🦱	man: medium-light skin tone, curly hair	People & Body	person
👨🏽‍🦱	man: medium skin tone, curly hair	People & Body	person
👨🏾‍🦱	man: medium-dark skin tone, curly hair	People & Body	person
👨🏿‍🦱	man: dark skin tone, curly hair	People & Body	person
👨‍🦳	man: white hair	People & Body	person
👨🏻‍🦳	man: light skin tone, white hair	People & Body	person
👨🏼‍🦳	man: medium-light skin tone, white hair	People & Body	person
👨🏽‍🦳	man: medium skin tone, white hair	People & Body	person
👨🏾‍🦳	man: medium-dark skin tone, white hair	People & Body	person
👨🏿‍🦳	man: dark skin tone, white hair	People & Body	person
👨‍🦲	man: bald	People & Body	person
👨🏻‍🦲	man: light skin tone, bald	People & Body	person
👨🏼‍🦲	man: medium-light skin tone, bald	People & Body	person
👨🏽‍🦲	man: medium skin tone, bald	People & Body	person
👨🏾‍🦲	man: medium-dark skin tone, bald	People & Body	person
👨🏿‍🦲	man: dark skin tone, bald	People & Body	person
👩	woman	People & Body	person
👩🏻	woman: light skin tone	People & Body	person
👩🏼	woman: medium-light skin tone	People & Body	person
👩🏽	woman: medium skin tone	People & Body	person
👩🏾	woman: medium-dark skin tone	People & Body	person
👩🏿	woman: dark skin tone	People & Body	person
👩‍🦰	woman: red hair	People & Body	person
👩🏻‍🦰	woman: light skin tone, red hair	People & Body	person
👩🏼‍🦰	woman: medium-light skin tone, red hair	People & Body	person
👩🏽‍🦰	woman: medium skin tone, red hair	People & Body	person
👩🏾‍🦰	woman: medium-dark skin tone, red hair	People & Body	person
👩🏿‍🦰	woman: dark skin tone, red hair	People & Body	person
🧑‍🦰	person: red hair	People & Body	person
🧑🏻‍🦰	person: light skin tone, red hair	People & Body	person
🧑🏼‍🦰	person: medium-light skin tone, red hair	People & Body	person
🧑🏽‍🦰	person: medium skin tone, red hair	People & Body	person
🧑🏾‍🦰	person: medium-dark skin tone, red hair	People & Body	person
🧑🏿‍🦰	person: dark skin tone, red hair	People & Body	person
👩‍🦱	woman: curly hair	People & Body	person
👩🏻‍🦱	woman: light skin tone, curly hair	People & Body	person
👩🏼‍🦱	woman: medium-light skin tone, curly hair	People & Body	person
👩🏽‍🦱	woman: medium skin tone, curly hair	People & Body	person
👩🏾‍🦱	woman: medium-dark skin tone, curly hair	People & Body	person
👩🏿‍🦱	woman: dark skin tone, curly hair	People & Body	person
🧑‍🦱	person: curly hair	People & Body	person
🧑🏻‍🦱	person: light skin tone, curly hair	People & Body	person
🧑🏼‍🦱	person: medium-light skin tone, curly hair	People & Body	person
🧑🏽‍🦱	person: medium skin tone, curly hair	People & Body	person
🧑🏾‍🦱	person: medium-dark skin tone, curly hair	People & Body	person
🧑🏿‍🦱	person: dark skin tone, curly hair	People & Body	person
👩‍🦳	woman: white hair	People & Body	person
👩🏻‍🦳	woman: light skin tone, white hair	People & Body	person
👩🏼‍🦳	woman: medium-light skin tone, white hair	People & Body	person
👩🏽‍🦳	woman: medium skin tone, white hair	People & Body	person
👩🏾‍🦳	woman: medium-dark skin tone, white hair	People & Body	person
👩🏿‍🦳	woman: dark skin tone, white hair	People & Body	person
🧑‍🦳	person: white hair	People & Body	person
🧑🏻‍🦳	person: light skin tone, white hair	People & Body	person
🧑🏼‍🦳	person: medium-light skin tone, white hair	People & Body	person
🧑🏽‍🦳	person: medium skin tone, white hair	People & Body	person
🧑🏾‍🦳	person: medium-dark skin tone, white hair	People & Body	person
🧑🏿‍🦳	person: dark skin tone, white hair	People & Body	person
👩‍🦲	woman: bald	People & Body	person
👩🏻‍🦲	woman: light skin tone, bald	People & Body	person
👩🏼‍🦲	woman: medium-light skin tone, bald	People & Body	person
👩🏽‍🦲	woman: medium skin tone, bald	People & Body	person
👩🏾‍🦲	woman: medium-dark skin tone, bald	People & Body	person
👩🏿‍🦲	woman: dark skin tone, bald	People & Body	person
🧑‍🦲	person: bald	People & Body	person
🧑🏻‍🦲	person: light skin tone, bald	People & Body	person
🧑🏼‍🦲	person: medium-light skin tone, bald	People & Body	person
🧑🏽‍🦲	person: medium skin tone, bald	People & Body	person
🧑🏾‍🦲	person: medium-dark skin tone, bald	People & Body	person
🧑🏿‍🦲	person: dark skin tone, bald	People & Body	person
👱‍♀️	woman: blond hair	People & Body	person
👱🏻‍♀️	woman: light skin tone, blond hair	People & Body	person
👱🏼‍♀️	woman: medium-light skin tone, blond hair	People & Body	person
👱🏽‍♀️	woman: medium skin tone, blond hair	People & Body	person
👱🏾‍♀️	woman: medium-dark skin tone, blond hair	People & Body	person
👱🏿‍♀️	woman: dark skin tone, blond hair	People & Body	person
👱‍♂️	man: blond hair	People & Body	person
👱🏻‍♂️	man: light skin tone, blond hair	People & Body	person
👱🏼‍♂️	man: medium-light skin tone, blond hair	People & Body	person
👱🏽‍♂️	man: medium skin tone, blond hair	People & Body	person
👱🏾‍♂️	man: medium-dark skin tone, blond hair	People & Body	person
👱🏿‍♂️	man: dark skin tone, blond hair	People & Body	person
🧓	older person	People & Body	person
🧓🏻	older person: light skin tone	People & Body	person
🧓🏼	older person: medium-light skin tone	People & Body	person
🧓🏽	older person: medium skin tone	People & Body	person
🧓🏾	older person: medium-dark skin tone	People & Body	person
🧓🏿	older person: dark skin tone	People & Body	person
👴	old man	People & Body	person
👴🏻	old man: light skin tone	People & Body	person
👴🏼	old man: medium-light skin tone	People & Body	person
👴🏽	old man: medium skin tone	People & Body	person
👴🏾	old man: medium-dark skin tone	People & Body	person
👴🏿	old man: dark skin tone	People & Body	person
👵	old woman	People & Body	person
👵🏻	old woman: light skin tone	People & Body	person
👵🏼	old woman: medium-light skin tone	People & Body	person
👵🏽	old woman: medium skin tone	People & Body	person
👵🏾	old woman: medium-dark skin tone	People & Body	person
👵🏿	old woman: dark skin tone	People & Body	person
🙍	person frowning	People & Body	person-gesture
🙍🏻	person frowning: light skin tone	People & Body	person-gesture
🙍🏼	person frowning: medium-light skin tone	People & Body	person-gesture
🙍🏽	person frowning: medium skin tone	People & Body	person-gesture
🙍🏾	person frowning: medium-dark skin tone	People & Body	person-gesture
🙍🏿	person frowning: dark skin tone	People & Body	person-gesture
🙍‍♂️	man frowning	People & Body	person-gesture
🙍🏻‍♂️	man frowning: light skin tone	People & Body	person-gesture
🙍🏼‍♂️	man frowning: medium-light skin tone	People & Body	person-gesture
🙍🏽‍♂️	man frowning: medium skin tone	People & Body	person-gesture
🙍🏾‍♂️	man frowning: medium-dark skin tone	People & Body	person-gesture
🙍🏿‍♂️	man frowning: dark skin tone	People & Body	person-gesture
🙍‍♀️	woman frowning	People & Body	person-gesture
🙍🏻‍♀️	woman frowning: light skin tone	People & Body	person-gesture
🙍🏼‍♀️	woman frowning: medium-light skin tone	People & Body	person-gesture
🙍🏽‍♀️	woman frowning: medium skin tone	People & Body	person-gesture
🙍🏾‍♀️	woman frowning: medium-dark skin tone	People & Body	person-gesture
🙍🏿‍♀️	woman frowning: dark skin tone	People & Body	person-gesture
🙎	person pouting	People & Body	person-gesture
🙎🏻	person pouting: light skin tone	People & Body	person-gesture
🙎🏼	person pouting: medium-light skin tone	People & Body	person-gesture
🙎🏽	person pouting: medium skin tone	People & Body	person-gesture
🙎🏾	person pouting: medium-dark skin tone	People & Body	person-gesture
🙎🏿	person pouting: dark skin tone	People & Body	person-gesture
🙎‍♂️	man pouting	People & Body	person-gesture
🙎🏻‍♂️	man pouting: light skin tone	People & Body	person-gesture
🙎🏼‍♂️	man pouting: medium-light skin tone	People & Body	person-gesture
🙎🏽‍♂️	man pouting: medium skin tone	People & Body	person-gesture
🙎🏾‍♂️	man pouting: medium-dark skin tone	People & Body	person-gesture
🙎🏿‍♂️	man pouting: dark skin tone	People & Body	person-gesture
🙎‍♀️	woman pouting	People & Body	person-gesture
🙎🏻‍♀️	woman pouting: light skin tone	People & Body	person-gesture
🙎🏼‍♀️	woman pouting: medium-light skin tone	People & Body	person-gesture
🙎🏽‍♀️	woman pouting: medium skin tone	People & Body	person-gesture
🙎🏾‍♀️	woman pouting: medium-dark skin tone	People & Body	person-gesture
🙎🏿‍♀️	woman pouting: dark skin tone	People & Body	person-gesture
🙅	person gesturing NO	People & Body	person-gesture
🙅🏻	person gesturing NO: light skin tone	People & Body	person-gesture
🙅🏼	person gesturing NO: medium-light skin tone	People & Body	person-gesture
🙅🏽	person gesturing NO: medium skin tone	People & Body	person-gesture
🙅🏾	person gesturing NO: medium-dark skin tone	People & Body	person-gesture
🙅🏿	person gesturing NO: dark skin tone	People & Body	person-gesture
🙅‍♂️	man gesturing NO	People & Body	person-gesture
🙅🏻‍♂️	man gesturing NO: light skin tone	People & Body	person-gesture
🙅🏼‍♂️	man gesturing NO: medium-light skin tone	People & Body	person-gesture
🙅🏽‍♂️	man gesturing NO: medium skin tone	People & Body	person-gesture
🙅🏾‍♂️	man gesturing NO: medium-dark skin tone	People & Body	person-gesture
🙅🏿‍♂️	man gesturing NO: dark skin tone	People & Body	person-gesture
🙅‍♀️	woman gesturing NO	People & Body	person-gesture
🙅🏻‍♀️	woman gesturing NO: light skin tone	People & Body	person-gesture
🙅🏼‍♀️	woman gesturing NO: medium-light skin tone	People & Body	person-gesture
🙅🏽‍♀️	woman gesturing NO: medium skin tone	People & Body	person-gesture
🙅🏾‍♀️	woman gesturing NO: medium-dark skin tone	People & Body	person-gesture
🙅🏿‍♀️	woman gesturing NO: dark skin tone	People & Body	person-gesture
🙆	person gesturing OK	People & Body	person-gesture
🙆🏻	person gesturing OK: light skin tone	People & Body	person-gesture
🙆🏼	person gesturing OK: medium-light skin tone	People & Body	person-gesture
🙆🏽	person gesturing OK: medium skin tone	People & Body	person-gesture
🙆🏾	person gesturing OK: medium-dark skin tone	People & Body	person-gesture
🙆🏿	person gesturing OK: dark skin tone	People & Body	person-gesture
🙆‍♂️	man gesturing OK	People & Body	person-gesture
🙆🏻‍♂️	man gesturing OK: light skin tone	People & Body	person-gesture
🙆🏼‍♂️	man gesturing OK: medium-light skin tone	People & Body	person-gesture
🙆🏽‍♂️	man gesturing OK: medium skin tone	People & Body	person-gesture
🙆🏾‍♂️	man gesturing OK: medium-dark skin tone	People & Body	person-gesture
🙆🏿‍♂️	man gesturing OK: dark skin tone	People & Body	person-gesture
🙆‍♀️	woman gesturing OK	People & Body	person-gesture
🙆🏻‍♀️	woman gesturing OK: light skin tone	People & Body	person-gesture
🙆🏼‍♀️	woman gesturing OK: medium-light skin tone	People & Body	person-gesture
🙆🏽‍♀️	woman gesturing OK: medium skin tone	People & Body	person-gesture
🙆🏾‍♀️	woman gesturing OK: medium-dark skin tone	People & Body	person-gesture
🙆🏿‍♀️	woman gesturing OK: dark skin tone	People & Body	person-gesture
💁	person tipping hand	People & Body	person-gesture
💁🏻	person tipping hand: light skin tone	People & Body	person-gesture
💁🏼	person tipping hand: medium-light skin tone	People & Body	person-gesture
💁🏽	person tipping hand: medium skin tone	People & Body	person-gesture
💁🏾	person tipping hand: medium-dark skin tone	People & Body	person-gesture
💁🏿	person tipping hand: dark skin tone	People & Body	person-gesture
💁‍♂️	man tipping hand	People & Body	person-gesture
💁🏻‍♂️	man tipping hand: light skin tone	People & Body	person-gesture
💁🏼‍♂️	man tipping hand: medium-light skin tone	People & Body	person-gesture
💁🏽‍♂️	man tipping hand: medium skin tone	People & Body	person-gesture
💁🏾‍♂️	man tipping hand: medium-dark skin tone	People & Body	person-gesture
💁🏿‍♂️	man tipping hand: dark skin tone	People & Body	person-gesture
💁‍♀️	woman tipping hand	People & Body	person-gesture
💁🏻‍♀️	woman tipping hand: light skin tone	People & Body	person-gesture
💁🏼‍♀️	woman tipping hand: medium-light skin tone	People & Body	person-gesture
💁🏽‍♀️	woman tipping hand: medium skin tone	People & Body	person-gesture
💁🏾‍♀️	woman tipping hand: medium-dark skin tone	People & Body	person-gesture
💁🏿‍♀️	woman tipping hand: dark skin tone	People & Body	person-gesture
🙋	person raising hand	People & Body	person-gesture
🙋🏻	person raising hand: light skin tone	People & Body	person-gesture
🙋🏼	person raising hand: medium-light skin tone	People & Body	person-gesture
🙋🏽	person raising hand: medium skin tone	People & Body	person-gesture
🙋🏾	person raising hand: medium-dark skin tone	People & Body	person-gesture
🙋🏿	person raising hand: dark skin tone	People & Body	person-gesture
🙋‍♂️	man raising hand	People & Body	person-gesture
🙋🏻‍♂️	man raising hand: light skin tone	People & Body	person-gesture
🙋🏼‍♂️	man raising hand: medium-light skin tone	People & Body	person-gesture
🙋🏽‍♂️	man raising hand: medium skin tone	People & Body	person-gesture
🙋🏾‍♂️	man raising hand: medium-dark skin tone	People & Body	person-gesture
🙋🏿‍♂️	man raising hand: dark skin tone	People & Body	person-gesture
🙋‍♀️	woman raising hand	People & Body	person-gesture
🙋🏻‍♀️	woman raising hand: light skin tone	People & Body	person-gesture
🙋🏼‍♀️	woman raising hand: medium-light skin tone	People & Body	person-gesture
🙋🏽‍♀️	woman raising hand: medium skin tone	People & Body	person-gesture
🙋🏾‍♀️	woman raising hand: medium-dark skin tone	People & Body	person-gesture
🙋🏿‍♀️	woman raising hand: dark skin tone	People & Body	person-gesture
🧏	deaf person	People & Body	person-gesture
🧏🏻	deaf person: light skin tone	People & Body	person-gesture
🧏🏼	deaf person: medium-light skin tone	People & Body	person-gesture
🧏🏽	deaf person: medium skin tone	People & Body	person-gesture
🧏🏾	deaf person: medium-dark skin tone	People & Body	person-gesture
🧏🏿	deaf person: dark skin tone	People & Body	person-gesture
🧏‍♂️	deaf man	People & Body	person-gesture
🧏🏻‍♂️	deaf man: light skin tone	People & Body	person-gesture
🧏🏼‍♂️	deaf man: medium-light skin tone	People & Body	person-gesture
🧏🏽‍♂️	deaf man: medium skin tone	People & Body	person-gesture
🧏🏾‍♂️	deaf man: medium-dark skin tone	People & Body	person-gesture
🧏🏿‍♂️	deaf man: dark skin tone	People & Body	person-gesture
🧏‍♀️	deaf woman	People & Body	person-gesture
🧏🏻‍♀️	deaf woman: light skin tone	People & Body	person-gesture
🧏🏼‍♀️	deaf woman: medium-light skin tone	People & Body	person-gesture
🧏🏽‍♀️	deaf woman: medium skin tone	People & Body	person-gesture
🧏🏾‍♀️	deaf woman: medium-dark skin tone	People & Body	person-gesture
"""

private const val EMOJI_DATA_1 = """
🧏🏿‍♀️	deaf woman: dark skin tone	People & Body	person-gesture
🙇	person bowing	People & Body	person-gesture
🙇🏻	person bowing: light skin tone	People & Body	person-gesture
🙇🏼	person bowing: medium-light skin tone	People & Body	person-gesture
🙇🏽	person bowing: medium skin tone	People & Body	person-gesture
🙇🏾	person bowing: medium-dark skin tone	People & Body	person-gesture
🙇🏿	person bowing: dark skin tone	People & Body	person-gesture
🙇‍♂️	man bowing	People & Body	person-gesture
🙇🏻‍♂️	man bowing: light skin tone	People & Body	person-gesture
🙇🏼‍♂️	man bowing: medium-light skin tone	People & Body	person-gesture
🙇🏽‍♂️	man bowing: medium skin tone	People & Body	person-gesture
🙇🏾‍♂️	man bowing: medium-dark skin tone	People & Body	person-gesture
🙇🏿‍♂️	man bowing: dark skin tone	People & Body	person-gesture
🙇‍♀️	woman bowing	People & Body	person-gesture
🙇🏻‍♀️	woman bowing: light skin tone	People & Body	person-gesture
🙇🏼‍♀️	woman bowing: medium-light skin tone	People & Body	person-gesture
🙇🏽‍♀️	woman bowing: medium skin tone	People & Body	person-gesture
🙇🏾‍♀️	woman bowing: medium-dark skin tone	People & Body	person-gesture
🙇🏿‍♀️	woman bowing: dark skin tone	People & Body	person-gesture
🤦	person facepalming	People & Body	person-gesture
🤦🏻	person facepalming: light skin tone	People & Body	person-gesture
🤦🏼	person facepalming: medium-light skin tone	People & Body	person-gesture
🤦🏽	person facepalming: medium skin tone	People & Body	person-gesture
🤦🏾	person facepalming: medium-dark skin tone	People & Body	person-gesture
🤦🏿	person facepalming: dark skin tone	People & Body	person-gesture
🤦‍♂️	man facepalming	People & Body	person-gesture
🤦🏻‍♂️	man facepalming: light skin tone	People & Body	person-gesture
🤦🏼‍♂️	man facepalming: medium-light skin tone	People & Body	person-gesture
🤦🏽‍♂️	man facepalming: medium skin tone	People & Body	person-gesture
🤦🏾‍♂️	man facepalming: medium-dark skin tone	People & Body	person-gesture
🤦🏿‍♂️	man facepalming: dark skin tone	People & Body	person-gesture
🤦‍♀️	woman facepalming	People & Body	person-gesture
🤦🏻‍♀️	woman facepalming: light skin tone	People & Body	person-gesture
🤦🏼‍♀️	woman facepalming: medium-light skin tone	People & Body	person-gesture
🤦🏽‍♀️	woman facepalming: medium skin tone	People & Body	person-gesture
🤦🏾‍♀️	woman facepalming: medium-dark skin tone	People & Body	person-gesture
🤦🏿‍♀️	woman facepalming: dark skin tone	People & Body	person-gesture
🤷	person shrugging	People & Body	person-gesture
🤷🏻	person shrugging: light skin tone	People & Body	person-gesture
🤷🏼	person shrugging: medium-light skin tone	People & Body	person-gesture
🤷🏽	person shrugging: medium skin tone	People & Body	person-gesture
🤷🏾	person shrugging: medium-dark skin tone	People & Body	person-gesture
🤷🏿	person shrugging: dark skin tone	People & Body	person-gesture
🤷‍♂️	man shrugging	People & Body	person-gesture
🤷🏻‍♂️	man shrugging: light skin tone	People & Body	person-gesture
🤷🏼‍♂️	man shrugging: medium-light skin tone	People & Body	person-gesture
🤷🏽‍♂️	man shrugging: medium skin tone	People & Body	person-gesture
🤷🏾‍♂️	man shrugging: medium-dark skin tone	People & Body	person-gesture
🤷🏿‍♂️	man shrugging: dark skin tone	People & Body	person-gesture
🤷‍♀️	woman shrugging	People & Body	person-gesture
🤷🏻‍♀️	woman shrugging: light skin tone	People & Body	person-gesture
🤷🏼‍♀️	woman shrugging: medium-light skin tone	People & Body	person-gesture
🤷🏽‍♀️	woman shrugging: medium skin tone	People & Body	person-gesture
🤷🏾‍♀️	woman shrugging: medium-dark skin tone	People & Body	person-gesture
🤷🏿‍♀️	woman shrugging: dark skin tone	People & Body	person-gesture
🧑‍⚕️	health worker	People & Body	person-role
🧑🏻‍⚕️	health worker: light skin tone	People & Body	person-role
🧑🏼‍⚕️	health worker: medium-light skin tone	People & Body	person-role
🧑🏽‍⚕️	health worker: medium skin tone	People & Body	person-role
🧑🏾‍⚕️	health worker: medium-dark skin tone	People & Body	person-role
🧑🏿‍⚕️	health worker: dark skin tone	People & Body	person-role
👨‍⚕️	man health worker	People & Body	person-role
👨🏻‍⚕️	man health worker: light skin tone	People & Body	person-role
👨🏼‍⚕️	man health worker: medium-light skin tone	People & Body	person-role
👨🏽‍⚕️	man health worker: medium skin tone	People & Body	person-role
👨🏾‍⚕️	man health worker: medium-dark skin tone	People & Body	person-role
👨🏿‍⚕️	man health worker: dark skin tone	People & Body	person-role
👩‍⚕️	woman health worker	People & Body	person-role
👩🏻‍⚕️	woman health worker: light skin tone	People & Body	person-role
👩🏼‍⚕️	woman health worker: medium-light skin tone	People & Body	person-role
👩🏽‍⚕️	woman health worker: medium skin tone	People & Body	person-role
👩🏾‍⚕️	woman health worker: medium-dark skin tone	People & Body	person-role
👩🏿‍⚕️	woman health worker: dark skin tone	People & Body	person-role
🧑‍🎓	student	People & Body	person-role
🧑🏻‍🎓	student: light skin tone	People & Body	person-role
🧑🏼‍🎓	student: medium-light skin tone	People & Body	person-role
🧑🏽‍🎓	student: medium skin tone	People & Body	person-role
🧑🏾‍🎓	student: medium-dark skin tone	People & Body	person-role
🧑🏿‍🎓	student: dark skin tone	People & Body	person-role
👨‍🎓	man student	People & Body	person-role
👨🏻‍🎓	man student: light skin tone	People & Body	person-role
👨🏼‍🎓	man student: medium-light skin tone	People & Body	person-role
👨🏽‍🎓	man student: medium skin tone	People & Body	person-role
👨🏾‍🎓	man student: medium-dark skin tone	People & Body	person-role
👨🏿‍🎓	man student: dark skin tone	People & Body	person-role
👩‍🎓	woman student	People & Body	person-role
👩🏻‍🎓	woman student: light skin tone	People & Body	person-role
👩🏼‍🎓	woman student: medium-light skin tone	People & Body	person-role
👩🏽‍🎓	woman student: medium skin tone	People & Body	person-role
👩🏾‍🎓	woman student: medium-dark skin tone	People & Body	person-role
👩🏿‍🎓	woman student: dark skin tone	People & Body	person-role
🧑‍🏫	teacher	People & Body	person-role
🧑🏻‍🏫	teacher: light skin tone	People & Body	person-role
🧑🏼‍🏫	teacher: medium-light skin tone	People & Body	person-role
🧑🏽‍🏫	teacher: medium skin tone	People & Body	person-role
🧑🏾‍🏫	teacher: medium-dark skin tone	People & Body	person-role
🧑🏿‍🏫	teacher: dark skin tone	People & Body	person-role
👨‍🏫	man teacher	People & Body	person-role
👨🏻‍🏫	man teacher: light skin tone	People & Body	person-role
👨🏼‍🏫	man teacher: medium-light skin tone	People & Body	person-role
👨🏽‍🏫	man teacher: medium skin tone	People & Body	person-role
👨🏾‍🏫	man teacher: medium-dark skin tone	People & Body	person-role
👨🏿‍🏫	man teacher: dark skin tone	People & Body	person-role
👩‍🏫	woman teacher	People & Body	person-role
👩🏻‍🏫	woman teacher: light skin tone	People & Body	person-role
👩🏼‍🏫	woman teacher: medium-light skin tone	People & Body	person-role
👩🏽‍🏫	woman teacher: medium skin tone	People & Body	person-role
👩🏾‍🏫	woman teacher: medium-dark skin tone	People & Body	person-role
👩🏿‍🏫	woman teacher: dark skin tone	People & Body	person-role
🧑‍⚖️	judge	People & Body	person-role
🧑🏻‍⚖️	judge: light skin tone	People & Body	person-role
🧑🏼‍⚖️	judge: medium-light skin tone	People & Body	person-role
🧑🏽‍⚖️	judge: medium skin tone	People & Body	person-role
🧑🏾‍⚖️	judge: medium-dark skin tone	People & Body	person-role
🧑🏿‍⚖️	judge: dark skin tone	People & Body	person-role
👨‍⚖️	man judge	People & Body	person-role
👨🏻‍⚖️	man judge: light skin tone	People & Body	person-role
👨🏼‍⚖️	man judge: medium-light skin tone	People & Body	person-role
👨🏽‍⚖️	man judge: medium skin tone	People & Body	person-role
👨🏾‍⚖️	man judge: medium-dark skin tone	People & Body	person-role
👨🏿‍⚖️	man judge: dark skin tone	People & Body	person-role
👩‍⚖️	woman judge	People & Body	person-role
👩🏻‍⚖️	woman judge: light skin tone	People & Body	person-role
👩🏼‍⚖️	woman judge: medium-light skin tone	People & Body	person-role
👩🏽‍⚖️	woman judge: medium skin tone	People & Body	person-role
👩🏾‍⚖️	woman judge: medium-dark skin tone	People & Body	person-role
👩🏿‍⚖️	woman judge: dark skin tone	People & Body	person-role
🧑‍🌾	farmer	People & Body	person-role
🧑🏻‍🌾	farmer: light skin tone	People & Body	person-role
🧑🏼‍🌾	farmer: medium-light skin tone	People & Body	person-role
🧑🏽‍🌾	farmer: medium skin tone	People & Body	person-role
🧑🏾‍🌾	farmer: medium-dark skin tone	People & Body	person-role
🧑🏿‍🌾	farmer: dark skin tone	People & Body	person-role
👨‍🌾	man farmer	People & Body	person-role
👨🏻‍🌾	man farmer: light skin tone	People & Body	person-role
👨🏼‍🌾	man farmer: medium-light skin tone	People & Body	person-role
👨🏽‍🌾	man farmer: medium skin tone	People & Body	person-role
👨🏾‍🌾	man farmer: medium-dark skin tone	People & Body	person-role
👨🏿‍🌾	man farmer: dark skin tone	People & Body	person-role
👩‍🌾	woman farmer	People & Body	person-role
👩🏻‍🌾	woman farmer: light skin tone	People & Body	person-role
👩🏼‍🌾	woman farmer: medium-light skin tone	People & Body	person-role
👩🏽‍🌾	woman farmer: medium skin tone	People & Body	person-role
👩🏾‍🌾	woman farmer: medium-dark skin tone	People & Body	person-role
👩🏿‍🌾	woman farmer: dark skin tone	People & Body	person-role
🧑‍🍳	cook	People & Body	person-role
🧑🏻‍🍳	cook: light skin tone	People & Body	person-role
🧑🏼‍🍳	cook: medium-light skin tone	People & Body	person-role
🧑🏽‍🍳	cook: medium skin tone	People & Body	person-role
🧑🏾‍🍳	cook: medium-dark skin tone	People & Body	person-role
🧑🏿‍🍳	cook: dark skin tone	People & Body	person-role
👨‍🍳	man cook	People & Body	person-role
👨🏻‍🍳	man cook: light skin tone	People & Body	person-role
👨🏼‍🍳	man cook: medium-light skin tone	People & Body	person-role
👨🏽‍🍳	man cook: medium skin tone	People & Body	person-role
👨🏾‍🍳	man cook: medium-dark skin tone	People & Body	person-role
👨🏿‍🍳	man cook: dark skin tone	People & Body	person-role
👩‍🍳	woman cook	People & Body	person-role
👩🏻‍🍳	woman cook: light skin tone	People & Body	person-role
👩🏼‍🍳	woman cook: medium-light skin tone	People & Body	person-role
👩🏽‍🍳	woman cook: medium skin tone	People & Body	person-role
👩🏾‍🍳	woman cook: medium-dark skin tone	People & Body	person-role
👩🏿‍🍳	woman cook: dark skin tone	People & Body	person-role
🧑‍🔧	mechanic	People & Body	person-role
🧑🏻‍🔧	mechanic: light skin tone	People & Body	person-role
🧑🏼‍🔧	mechanic: medium-light skin tone	People & Body	person-role
🧑🏽‍🔧	mechanic: medium skin tone	People & Body	person-role
🧑🏾‍🔧	mechanic: medium-dark skin tone	People & Body	person-role
🧑🏿‍🔧	mechanic: dark skin tone	People & Body	person-role
👨‍🔧	man mechanic	People & Body	person-role
👨🏻‍🔧	man mechanic: light skin tone	People & Body	person-role
👨🏼‍🔧	man mechanic: medium-light skin tone	People & Body	person-role
👨🏽‍🔧	man mechanic: medium skin tone	People & Body	person-role
👨🏾‍🔧	man mechanic: medium-dark skin tone	People & Body	person-role
👨🏿‍🔧	man mechanic: dark skin tone	People & Body	person-role
👩‍🔧	woman mechanic	People & Body	person-role
👩🏻‍🔧	woman mechanic: light skin tone	People & Body	person-role
👩🏼‍🔧	woman mechanic: medium-light skin tone	People & Body	person-role
👩🏽‍🔧	woman mechanic: medium skin tone	People & Body	person-role
👩🏾‍🔧	woman mechanic: medium-dark skin tone	People & Body	person-role
👩🏿‍🔧	woman mechanic: dark skin tone	People & Body	person-role
🧑‍🏭	factory worker	People & Body	person-role
🧑🏻‍🏭	factory worker: light skin tone	People & Body	person-role
🧑🏼‍🏭	factory worker: medium-light skin tone	People & Body	person-role
🧑🏽‍🏭	factory worker: medium skin tone	People & Body	person-role
🧑🏾‍🏭	factory worker: medium-dark skin tone	People & Body	person-role
🧑🏿‍🏭	factory worker: dark skin tone	People & Body	person-role
👨‍🏭	man factory worker	People & Body	person-role
👨🏻‍🏭	man factory worker: light skin tone	People & Body	person-role
👨🏼‍🏭	man factory worker: medium-light skin tone	People & Body	person-role
👨🏽‍🏭	man factory worker: medium skin tone	People & Body	person-role
👨🏾‍🏭	man factory worker: medium-dark skin tone	People & Body	person-role
👨🏿‍🏭	man factory worker: dark skin tone	People & Body	person-role
👩‍🏭	woman factory worker	People & Body	person-role
👩🏻‍🏭	woman factory worker: light skin tone	People & Body	person-role
👩🏼‍🏭	woman factory worker: medium-light skin tone	People & Body	person-role
👩🏽‍🏭	woman factory worker: medium skin tone	People & Body	person-role
👩🏾‍🏭	woman factory worker: medium-dark skin tone	People & Body	person-role
👩🏿‍🏭	woman factory worker: dark skin tone	People & Body	person-role
🧑‍💼	office worker	People & Body	person-role
🧑🏻‍💼	office worker: light skin tone	People & Body	person-role
🧑🏼‍💼	office worker: medium-light skin tone	People & Body	person-role
🧑🏽‍💼	office worker: medium skin tone	People & Body	person-role
🧑🏾‍💼	office worker: medium-dark skin tone	People & Body	person-role
🧑🏿‍💼	office worker: dark skin tone	People & Body	person-role
👨‍💼	man office worker	People & Body	person-role
👨🏻‍💼	man office worker: light skin tone	People & Body	person-role
👨🏼‍💼	man office worker: medium-light skin tone	People & Body	person-role
👨🏽‍💼	man office worker: medium skin tone	People & Body	person-role
👨🏾‍💼	man office worker: medium-dark skin tone	People & Body	person-role
👨🏿‍💼	man office worker: dark skin tone	People & Body	person-role
👩‍💼	woman office worker	People & Body	person-role
👩🏻‍💼	woman office worker: light skin tone	People & Body	person-role
👩🏼‍💼	woman office worker: medium-light skin tone	People & Body	person-role
👩🏽‍💼	woman office worker: medium skin tone	People & Body	person-role
👩🏾‍💼	woman office worker: medium-dark skin tone	People & Body	person-role
👩🏿‍💼	woman office worker: dark skin tone	People & Body	person-role
🧑‍🔬	scientist	People & Body	person-role
🧑🏻‍🔬	scientist: light skin tone	People & Body	person-role
🧑🏼‍🔬	scientist: medium-light skin tone	People & Body	person-role
🧑🏽‍🔬	scientist: medium skin tone	People & Body	person-role
🧑🏾‍🔬	scientist: medium-dark skin tone	People & Body	person-role
🧑🏿‍🔬	scientist: dark skin tone	People & Body	person-role
👨‍🔬	man scientist	People & Body	person-role
👨🏻‍🔬	man scientist: light skin tone	People & Body	person-role
👨🏼‍🔬	man scientist: medium-light skin tone	People & Body	person-role
👨🏽‍🔬	man scientist: medium skin tone	People & Body	person-role
👨🏾‍🔬	man scientist: medium-dark skin tone	People & Body	person-role
👨🏿‍🔬	man scientist: dark skin tone	People & Body	person-role
👩‍🔬	woman scientist	People & Body	person-role
👩🏻‍🔬	woman scientist: light skin tone	People & Body	person-role
👩🏼‍🔬	woman scientist: medium-light skin tone	People & Body	person-role
👩🏽‍🔬	woman scientist: medium skin tone	People & Body	person-role
👩🏾‍🔬	woman scientist: medium-dark skin tone	People & Body	person-role
👩🏿‍🔬	woman scientist: dark skin tone	People & Body	person-role
🧑‍💻	technologist	People & Body	person-role
🧑🏻‍💻	technologist: light skin tone	People & Body	person-role
🧑🏼‍💻	technologist: medium-light skin tone	People & Body	person-role
🧑🏽‍💻	technologist: medium skin tone	People & Body	person-role
🧑🏾‍💻	technologist: medium-dark skin tone	People & Body	person-role
🧑🏿‍💻	technologist: dark skin tone	People & Body	person-role
👨‍💻	man technologist	People & Body	person-role
👨🏻‍💻	man technologist: light skin tone	People & Body	person-role
👨🏼‍💻	man technologist: medium-light skin tone	People & Body	person-role
👨🏽‍💻	man technologist: medium skin tone	People & Body	person-role
👨🏾‍💻	man technologist: medium-dark skin tone	People & Body	person-role
👨🏿‍💻	man technologist: dark skin tone	People & Body	person-role
👩‍💻	woman technologist	People & Body	person-role
👩🏻‍💻	woman technologist: light skin tone	People & Body	person-role
👩🏼‍💻	woman technologist: medium-light skin tone	People & Body	person-role
👩🏽‍💻	woman technologist: medium skin tone	People & Body	person-role
👩🏾‍💻	woman technologist: medium-dark skin tone	People & Body	person-role
👩🏿‍💻	woman technologist: dark skin tone	People & Body	person-role
🧑‍🎤	singer	People & Body	person-role
🧑🏻‍🎤	singer: light skin tone	People & Body	person-role
🧑🏼‍🎤	singer: medium-light skin tone	People & Body	person-role
🧑🏽‍🎤	singer: medium skin tone	People & Body	person-role
🧑🏾‍🎤	singer: medium-dark skin tone	People & Body	person-role
🧑🏿‍🎤	singer: dark skin tone	People & Body	person-role
👨‍🎤	man singer	People & Body	person-role
👨🏻‍🎤	man singer: light skin tone	People & Body	person-role
👨🏼‍🎤	man singer: medium-light skin tone	People & Body	person-role
👨🏽‍🎤	man singer: medium skin tone	People & Body	person-role
👨🏾‍🎤	man singer: medium-dark skin tone	People & Body	person-role
👨🏿‍🎤	man singer: dark skin tone	People & Body	person-role
👩‍🎤	woman singer	People & Body	person-role
👩🏻‍🎤	woman singer: light skin tone	People & Body	person-role
👩🏼‍🎤	woman singer: medium-light skin tone	People & Body	person-role
👩🏽‍🎤	woman singer: medium skin tone	People & Body	person-role
👩🏾‍🎤	woman singer: medium-dark skin tone	People & Body	person-role
👩🏿‍🎤	woman singer: dark skin tone	People & Body	person-role
🧑‍🎨	artist	People & Body	person-role
🧑🏻‍🎨	artist: light skin tone	People & Body	person-role
🧑🏼‍🎨	artist: medium-light skin tone	People & Body	person-role
🧑🏽‍🎨	artist: medium skin tone	People & Body	person-role
🧑🏾‍🎨	artist: medium-dark skin tone	People & Body	person-role
🧑🏿‍🎨	artist: dark skin tone	People & Body	person-role
👨‍🎨	man artist	People & Body	person-role
👨🏻‍🎨	man artist: light skin tone	People & Body	person-role
👨🏼‍🎨	man artist: medium-light skin tone	People & Body	person-role
👨🏽‍🎨	man artist: medium skin tone	People & Body	person-role
👨🏾‍🎨	man artist: medium-dark skin tone	People & Body	person-role
👨🏿‍🎨	man artist: dark skin tone	People & Body	person-role
👩‍🎨	woman artist	People & Body	person-role
👩🏻‍🎨	woman artist: light skin tone	People & Body	person-role
👩🏼‍🎨	woman artist: medium-light skin tone	People & Body	person-role
👩🏽‍🎨	woman artist: medium skin tone	People & Body	person-role
👩🏾‍🎨	woman artist: medium-dark skin tone	People & Body	person-role
👩🏿‍🎨	woman artist: dark skin tone	People & Body	person-role
🧑‍✈️	pilot	People & Body	person-role
🧑🏻‍✈️	pilot: light skin tone	People & Body	person-role
🧑🏼‍✈️	pilot: medium-light skin tone	People & Body	person-role
🧑🏽‍✈️	pilot: medium skin tone	People & Body	person-role
🧑🏾‍✈️	pilot: medium-dark skin tone	People & Body	person-role
🧑🏿‍✈️	pilot: dark skin tone	People & Body	person-role
👨‍✈️	man pilot	People & Body	person-role
👨🏻‍✈️	man pilot: light skin tone	People & Body	person-role
👨🏼‍✈️	man pilot: medium-light skin tone	People & Body	person-role
👨🏽‍✈️	man pilot: medium skin tone	People & Body	person-role
👨🏾‍✈️	man pilot: medium-dark skin tone	People & Body	person-role
👨🏿‍✈️	man pilot: dark skin tone	People & Body	person-role
👩‍✈️	woman pilot	People & Body	person-role
👩🏻‍✈️	woman pilot: light skin tone	People & Body	person-role
👩🏼‍✈️	woman pilot: medium-light skin tone	People & Body	person-role
👩🏽‍✈️	woman pilot: medium skin tone	People & Body	person-role
👩🏾‍✈️	woman pilot: medium-dark skin tone	People & Body	person-role
👩🏿‍✈️	woman pilot: dark skin tone	People & Body	person-role
🧑‍🚀	astronaut	People & Body	person-role
🧑🏻‍🚀	astronaut: light skin tone	People & Body	person-role
🧑🏼‍🚀	astronaut: medium-light skin tone	People & Body	person-role
🧑🏽‍🚀	astronaut: medium skin tone	People & Body	person-role
🧑🏾‍🚀	astronaut: medium-dark skin tone	People & Body	person-role
🧑🏿‍🚀	astronaut: dark skin tone	People & Body	person-role
👨‍🚀	man astronaut	People & Body	person-role
👨🏻‍🚀	man astronaut: light skin tone	People & Body	person-role
👨🏼‍🚀	man astronaut: medium-light skin tone	People & Body	person-role
👨🏽‍🚀	man astronaut: medium skin tone	People & Body	person-role
👨🏾‍🚀	man astronaut: medium-dark skin tone	People & Body	person-role
👨🏿‍🚀	man astronaut: dark skin tone	People & Body	person-role
👩‍🚀	woman astronaut	People & Body	person-role
👩🏻‍🚀	woman astronaut: light skin tone	People & Body	person-role
👩🏼‍🚀	woman astronaut: medium-light skin tone	People & Body	person-role
👩🏽‍🚀	woman astronaut: medium skin tone	People & Body	person-role
👩🏾‍🚀	woman astronaut: medium-dark skin tone	People & Body	person-role
👩🏿‍🚀	woman astronaut: dark skin tone	People & Body	person-role
🧑‍🚒	firefighter	People & Body	person-role
🧑🏻‍🚒	firefighter: light skin tone	People & Body	person-role
🧑🏼‍🚒	firefighter: medium-light skin tone	People & Body	person-role
🧑🏽‍🚒	firefighter: medium skin tone	People & Body	person-role
🧑🏾‍🚒	firefighter: medium-dark skin tone	People & Body	person-role
🧑🏿‍🚒	firefighter: dark skin tone	People & Body	person-role
👨‍🚒	man firefighter	People & Body	person-role
👨🏻‍🚒	man firefighter: light skin tone	People & Body	person-role
👨🏼‍🚒	man firefighter: medium-light skin tone	People & Body	person-role
👨🏽‍🚒	man firefighter: medium skin tone	People & Body	person-role
👨🏾‍🚒	man firefighter: medium-dark skin tone	People & Body	person-role
👨🏿‍🚒	man firefighter: dark skin tone	People & Body	person-role
👩‍🚒	woman firefighter	People & Body	person-role
👩🏻‍🚒	woman firefighter: light skin tone	People & Body	person-role
👩🏼‍🚒	woman firefighter: medium-light skin tone	People & Body	person-role
👩🏽‍🚒	woman firefighter: medium skin tone	People & Body	person-role
👩🏾‍🚒	woman firefighter: medium-dark skin tone	People & Body	person-role
👩🏿‍🚒	woman firefighter: dark skin tone	People & Body	person-role
👮	police officer	People & Body	person-role
👮🏻	police officer: light skin tone	People & Body	person-role
👮🏼	police officer: medium-light skin tone	People & Body	person-role
👮🏽	police officer: medium skin tone	People & Body	person-role
👮🏾	police officer: medium-dark skin tone	People & Body	person-role
👮🏿	police officer: dark skin tone	People & Body	person-role
👮‍♂️	man police officer	People & Body	person-role
👮🏻‍♂️	man police officer: light skin tone	People & Body	person-role
👮🏼‍♂️	man police officer: medium-light skin tone	People & Body	person-role
👮🏽‍♂️	man police officer: medium skin tone	People & Body	person-role
👮🏾‍♂️	man police officer: medium-dark skin tone	People & Body	person-role
👮🏿‍♂️	man police officer: dark skin tone	People & Body	person-role
👮‍♀️	woman police officer	People & Body	person-role
👮🏻‍♀️	woman police officer: light skin tone	People & Body	person-role
👮🏼‍♀️	woman police officer: medium-light skin tone	People & Body	person-role
👮🏽‍♀️	woman police officer: medium skin tone	People & Body	person-role
👮🏾‍♀️	woman police officer: medium-dark skin tone	People & Body	person-role
👮🏿‍♀️	woman police officer: dark skin tone	People & Body	person-role
🕵️	detective	People & Body	person-role
🕵🏻	detective: light skin tone	People & Body	person-role
🕵🏼	detective: medium-light skin tone	People & Body	person-role
🕵🏽	detective: medium skin tone	People & Body	person-role
🕵🏾	detective: medium-dark skin tone	People & Body	person-role
🕵🏿	detective: dark skin tone	People & Body	person-role
🕵️‍♂️	man detective	People & Body	person-role
🕵🏻‍♂️	man detective: light skin tone	People & Body	person-role
🕵🏼‍♂️	man detective: medium-light skin tone	People & Body	person-role
🕵🏽‍♂️	man detective: medium skin tone	People & Body	person-role
🕵🏾‍♂️	man detective: medium-dark skin tone	People & Body	person-role
🕵🏿‍♂️	man detective: dark skin tone	People & Body	person-role
🕵️‍♀️	woman detective	People & Body	person-role
🕵🏻‍♀️	woman detective: light skin tone	People & Body	person-role
🕵🏼‍♀️	woman detective: medium-light skin tone	People & Body	person-role
🕵🏽‍♀️	woman detective: medium skin tone	People & Body	person-role
🕵🏾‍♀️	woman detective: medium-dark skin tone	People & Body	person-role
🕵🏿‍♀️	woman detective: dark skin tone	People & Body	person-role
💂	guard	People & Body	person-role
💂🏻	guard: light skin tone	People & Body	person-role
💂🏼	guard: medium-light skin tone	People & Body	person-role
💂🏽	guard: medium skin tone	People & Body	person-role
💂🏾	guard: medium-dark skin tone	People & Body	person-role
💂🏿	guard: dark skin tone	People & Body	person-role
💂‍♂️	man guard	People & Body	person-role
💂🏻‍♂️	man guard: light skin tone	People & Body	person-role
💂🏼‍♂️	man guard: medium-light skin tone	People & Body	person-role
💂🏽‍♂️	man guard: medium skin tone	People & Body	person-role
💂🏾‍♂️	man guard: medium-dark skin tone	People & Body	person-role
💂🏿‍♂️	man guard: dark skin tone	People & Body	person-role
💂‍♀️	woman guard	People & Body	person-role
💂🏻‍♀️	woman guard: light skin tone	People & Body	person-role
💂🏼‍♀️	woman guard: medium-light skin tone	People & Body	person-role
💂🏽‍♀️	woman guard: medium skin tone	People & Body	person-role
💂🏾‍♀️	woman guard: medium-dark skin tone	People & Body	person-role
💂🏿‍♀️	woman guard: dark skin tone	People & Body	person-role
🥷	ninja	People & Body	person-role
🥷🏻	ninja: light skin tone	People & Body	person-role
🥷🏼	ninja: medium-light skin tone	People & Body	person-role
🥷🏽	ninja: medium skin tone	People & Body	person-role
🥷🏾	ninja: medium-dark skin tone	People & Body	person-role
🥷🏿	ninja: dark skin tone	People & Body	person-role
👷	construction worker	People & Body	person-role
👷🏻	construction worker: light skin tone	People & Body	person-role
👷🏼	construction worker: medium-light skin tone	People & Body	person-role
👷🏽	construction worker: medium skin tone	People & Body	person-role
👷🏾	construction worker: medium-dark skin tone	People & Body	person-role
👷🏿	construction worker: dark skin tone	People & Body	person-role
👷‍♂️	man construction worker	People & Body	person-role
👷🏻‍♂️	man construction worker: light skin tone	People & Body	person-role
👷🏼‍♂️	man construction worker: medium-light skin tone	People & Body	person-role
👷🏽‍♂️	man construction worker: medium skin tone	People & Body	person-role
👷🏾‍♂️	man construction worker: medium-dark skin tone	People & Body	person-role
👷🏿‍♂️	man construction worker: dark skin tone	People & Body	person-role
👷‍♀️	woman construction worker	People & Body	person-role
👷🏻‍♀️	woman construction worker: light skin tone	People & Body	person-role
👷🏼‍♀️	woman construction worker: medium-light skin tone	People & Body	person-role
👷🏽‍♀️	woman construction worker: medium skin tone	People & Body	person-role
👷🏾‍♀️	woman construction worker: medium-dark skin tone	People & Body	person-role
👷🏿‍♀️	woman construction worker: dark skin tone	People & Body	person-role
🫅	person with crown	People & Body	person-role
🫅🏻	person with crown: light skin tone	People & Body	person-role
🫅🏼	person with crown: medium-light skin tone	People & Body	person-role
🫅🏽	person with crown: medium skin tone	People & Body	person-role
🫅🏾	person with crown: medium-dark skin tone	People & Body	person-role
🫅🏿	person with crown: dark skin tone	People & Body	person-role
🤴	prince	People & Body	person-role
🤴🏻	prince: light skin tone	People & Body	person-role
🤴🏼	prince: medium-light skin tone	People & Body	person-role
🤴🏽	prince: medium skin tone	People & Body	person-role
🤴🏾	prince: medium-dark skin tone	People & Body	person-role
🤴🏿	prince: dark skin tone	People & Body	person-role
👸	princess	People & Body	person-role
👸🏻	princess: light skin tone	People & Body	person-role
👸🏼	princess: medium-light skin tone	People & Body	person-role
👸🏽	princess: medium skin tone	People & Body	person-role
👸🏾	princess: medium-dark skin tone	People & Body	person-role
👸🏿	princess: dark skin tone	People & Body	person-role
👳	person wearing turban	People & Body	person-role
👳🏻	person wearing turban: light skin tone	People & Body	person-role
👳🏼	person wearing turban: medium-light skin tone	People & Body	person-role
👳🏽	person wearing turban: medium skin tone	People & Body	person-role
👳🏾	person wearing turban: medium-dark skin tone	People & Body	person-role
👳🏿	person wearing turban: dark skin tone	People & Body	person-role
👳‍♂️	man wearing turban	People & Body	person-role
👳🏻‍♂️	man wearing turban: light skin tone	People & Body	person-role
👳🏼‍♂️	man wearing turban: medium-light skin tone	People & Body	person-role
👳🏽‍♂️	man wearing turban: medium skin tone	People & Body	person-role
👳🏾‍♂️	man wearing turban: medium-dark skin tone	People & Body	person-role
👳🏿‍♂️	man wearing turban: dark skin tone	People & Body	person-role
👳‍♀️	woman wearing turban	People & Body	person-role
👳🏻‍♀️	woman wearing turban: light skin tone	People & Body	person-role
👳🏼‍♀️	woman wearing turban: medium-light skin tone	People & Body	person-role
👳🏽‍♀️	woman wearing turban: medium skin tone	People & Body	person-role
👳🏾‍♀️	woman wearing turban: medium-dark skin tone	People & Body	person-role
👳🏿‍♀️	woman wearing turban: dark skin tone	People & Body	person-role
👲	person with skullcap	People & Body	person-role
👲🏻	person with skullcap: light skin tone	People & Body	person-role
👲🏼	person with skullcap: medium-light skin tone	People & Body	person-role
👲🏽	person with skullcap: medium skin tone	People & Body	person-role
👲🏾	person with skullcap: medium-dark skin tone	People & Body	person-role
👲🏿	person with skullcap: dark skin tone	People & Body	person-role
🧕	woman with headscarf	People & Body	person-role
🧕🏻	woman with headscarf: light skin tone	People & Body	person-role
🧕🏼	woman with headscarf: medium-light skin tone	People & Body	person-role
🧕🏽	woman with headscarf: medium skin tone	People & Body	person-role
🧕🏾	woman with headscarf: medium-dark skin tone	People & Body	person-role
🧕🏿	woman with headscarf: dark skin tone	People & Body	person-role
🤵	person in tuxedo	People & Body	person-role
🤵🏻	person in tuxedo: light skin tone	People & Body	person-role
🤵🏼	person in tuxedo: medium-light skin tone	People & Body	person-role
🤵🏽	person in tuxedo: medium skin tone	People & Body	person-role
🤵🏾	person in tuxedo: medium-dark skin tone	People & Body	person-role
🤵🏿	person in tuxedo: dark skin tone	People & Body	person-role
🤵‍♂️	man in tuxedo	People & Body	person-role
🤵🏻‍♂️	man in tuxedo: light skin tone	People & Body	person-role
🤵🏼‍♂️	man in tuxedo: medium-light skin tone	People & Body	person-role
🤵🏽‍♂️	man in tuxedo: medium skin tone	People & Body	person-role
🤵🏾‍♂️	man in tuxedo: medium-dark skin tone	People & Body	person-role
🤵🏿‍♂️	man in tuxedo: dark skin tone	People & Body	person-role
🤵‍♀️	woman in tuxedo	People & Body	person-role
🤵🏻‍♀️	woman in tuxedo: light skin tone	People & Body	person-role
🤵🏼‍♀️	woman in tuxedo: medium-light skin tone	People & Body	person-role
🤵🏽‍♀️	woman in tuxedo: medium skin tone	People & Body	person-role
🤵🏾‍♀️	woman in tuxedo: medium-dark skin tone	People & Body	person-role
🤵🏿‍♀️	woman in tuxedo: dark skin tone	People & Body	person-role
👰	person with veil	People & Body	person-role
👰🏻	person with veil: light skin tone	People & Body	person-role
👰🏼	person with veil: medium-light skin tone	People & Body	person-role
👰🏽	person with veil: medium skin tone	People & Body	person-role
👰🏾	person with veil: medium-dark skin tone	People & Body	person-role
👰🏿	person with veil: dark skin tone	People & Body	person-role
👰‍♂️	man with veil	People & Body	person-role
👰🏻‍♂️	man with veil: light skin tone	People & Body	person-role
👰🏼‍♂️	man with veil: medium-light skin tone	People & Body	person-role
👰🏽‍♂️	man with veil: medium skin tone	People & Body	person-role
👰🏾‍♂️	man with veil: medium-dark skin tone	People & Body	person-role
👰🏿‍♂️	man with veil: dark skin tone	People & Body	person-role
👰‍♀️	woman with veil	People & Body	person-role
👰🏻‍♀️	woman with veil: light skin tone	People & Body	person-role
👰🏼‍♀️	woman with veil: medium-light skin tone	People & Body	person-role
👰🏽‍♀️	woman with veil: medium skin tone	People & Body	person-role
👰🏾‍♀️	woman with veil: medium-dark skin tone	People & Body	person-role
👰🏿‍♀️	woman with veil: dark skin tone	People & Body	person-role
🤰	pregnant woman	People & Body	person-role
🤰🏻	pregnant woman: light skin tone	People & Body	person-role
🤰🏼	pregnant woman: medium-light skin tone	People & Body	person-role
🤰🏽	pregnant woman: medium skin tone	People & Body	person-role
🤰🏾	pregnant woman: medium-dark skin tone	People & Body	person-role
🤰🏿	pregnant woman: dark skin tone	People & Body	person-role
🫃	pregnant man	People & Body	person-role
🫃🏻	pregnant man: light skin tone	People & Body	person-role
🫃🏼	pregnant man: medium-light skin tone	People & Body	person-role
🫃🏽	pregnant man: medium skin tone	People & Body	person-role
🫃🏾	pregnant man: medium-dark skin tone	People & Body	person-role
🫃🏿	pregnant man: dark skin tone	People & Body	person-role
🫄	pregnant person	People & Body	person-role
🫄🏻	pregnant person: light skin tone	People & Body	person-role
🫄🏼	pregnant person: medium-light skin tone	People & Body	person-role
🫄🏽	pregnant person: medium skin tone	People & Body	person-role
🫄🏾	pregnant person: medium-dark skin tone	People & Body	person-role
🫄🏿	pregnant person: dark skin tone	People & Body	person-role
🤱	breast-feeding	People & Body	person-role
🤱🏻	breast-feeding: light skin tone	People & Body	person-role
🤱🏼	breast-feeding: medium-light skin tone	People & Body	person-role
🤱🏽	breast-feeding: medium skin tone	People & Body	person-role
🤱🏾	breast-feeding: medium-dark skin tone	People & Body	person-role
🤱🏿	breast-feeding: dark skin tone	People & Body	person-role
👩‍🍼	woman feeding baby	People & Body	person-role
👩🏻‍🍼	woman feeding baby: light skin tone	People & Body	person-role
👩🏼‍🍼	woman feeding baby: medium-light skin tone	People & Body	person-role
👩🏽‍🍼	woman feeding baby: medium skin tone	People & Body	person-role
👩🏾‍🍼	woman feeding baby: medium-dark skin tone	People & Body	person-role
👩🏿‍🍼	woman feeding baby: dark skin tone	People & Body	person-role
👨‍🍼	man feeding baby	People & Body	person-role
👨🏻‍🍼	man feeding baby: light skin tone	People & Body	person-role
👨🏼‍🍼	man feeding baby: medium-light skin tone	People & Body	person-role
👨🏽‍🍼	man feeding baby: medium skin tone	People & Body	person-role
👨🏾‍🍼	man feeding baby: medium-dark skin tone	People & Body	person-role
👨🏿‍🍼	man feeding baby: dark skin tone	People & Body	person-role
🧑‍🍼	person feeding baby	People & Body	person-role
🧑🏻‍🍼	person feeding baby: light skin tone	People & Body	person-role
🧑🏼‍🍼	person feeding baby: medium-light skin tone	People & Body	person-role
🧑🏽‍🍼	person feeding baby: medium skin tone	People & Body	person-role
🧑🏾‍🍼	person feeding baby: medium-dark skin tone	People & Body	person-role
🧑🏿‍🍼	person feeding baby: dark skin tone	People & Body	person-role
👼	baby angel	People & Body	person-fantasy
👼🏻	baby angel: light skin tone	People & Body	person-fantasy
👼🏼	baby angel: medium-light skin tone	People & Body	person-fantasy
👼🏽	baby angel: medium skin tone	People & Body	person-fantasy
👼🏾	baby angel: medium-dark skin tone	People & Body	person-fantasy
👼🏿	baby angel: dark skin tone	People & Body	person-fantasy
🎅	Santa Claus	People & Body	person-fantasy
🎅🏻	Santa Claus: light skin tone	People & Body	person-fantasy
🎅🏼	Santa Claus: medium-light skin tone	People & Body	person-fantasy
🎅🏽	Santa Claus: medium skin tone	People & Body	person-fantasy
🎅🏾	Santa Claus: medium-dark skin tone	People & Body	person-fantasy
🎅🏿	Santa Claus: dark skin tone	People & Body	person-fantasy
🤶	Mrs. Claus	People & Body	person-fantasy
🤶🏻	Mrs. Claus: light skin tone	People & Body	person-fantasy
🤶🏼	Mrs. Claus: medium-light skin tone	People & Body	person-fantasy
🤶🏽	Mrs. Claus: medium skin tone	People & Body	person-fantasy
🤶🏾	Mrs. Claus: medium-dark skin tone	People & Body	person-fantasy
🤶🏿	Mrs. Claus: dark skin tone	People & Body	person-fantasy
🧑‍🎄	Mx Claus	People & Body	person-fantasy
🧑🏻‍🎄	Mx Claus: light skin tone	People & Body	person-fantasy
🧑🏼‍🎄	Mx Claus: medium-light skin tone	People & Body	person-fantasy
🧑🏽‍🎄	Mx Claus: medium skin tone	People & Body	person-fantasy
🧑🏾‍🎄	Mx Claus: medium-dark skin tone	People & Body	person-fantasy
🧑🏿‍🎄	Mx Claus: dark skin tone	People & Body	person-fantasy
🦸	superhero	People & Body	person-fantasy
🦸🏻	superhero: light skin tone	People & Body	person-fantasy
🦸🏼	superhero: medium-light skin tone	People & Body	person-fantasy
🦸🏽	superhero: medium skin tone	People & Body	person-fantasy
🦸🏾	superhero: medium-dark skin tone	People & Body	person-fantasy
🦸🏿	superhero: dark skin tone	People & Body	person-fantasy
🦸‍♂️	man superhero	People & Body	person-fantasy
🦸🏻‍♂️	man superhero: light skin tone	People & Body	person-fantasy
🦸🏼‍♂️	man superhero: medium-light skin tone	People & Body	person-fantasy
🦸🏽‍♂️	man superhero: medium skin tone	People & Body	person-fantasy
🦸🏾‍♂️	man superhero: medium-dark skin tone	People & Body	person-fantasy
🦸🏿‍♂️	man superhero: dark skin tone	People & Body	person-fantasy
🦸‍♀️	woman superhero	People & Body	person-fantasy
🦸🏻‍♀️	woman superhero: light skin tone	People & Body	person-fantasy
🦸🏼‍♀️	woman superhero: medium-light skin tone	People & Body	person-fantasy
🦸🏽‍♀️	woman superhero: medium skin tone	People & Body	person-fantasy
🦸🏾‍♀️	woman superhero: medium-dark skin tone	People & Body	person-fantasy
🦸🏿‍♀️	woman superhero: dark skin tone	People & Body	person-fantasy
🦹	supervillain	People & Body	person-fantasy
🦹🏻	supervillain: light skin tone	People & Body	person-fantasy
🦹🏼	supervillain: medium-light skin tone	People & Body	person-fantasy
🦹🏽	supervillain: medium skin tone	People & Body	person-fantasy
🦹🏾	supervillain: medium-dark skin tone	People & Body	person-fantasy
🦹🏿	supervillain: dark skin tone	People & Body	person-fantasy
🦹‍♂️	man supervillain	People & Body	person-fantasy
🦹🏻‍♂️	man supervillain: light skin tone	People & Body	person-fantasy
🦹🏼‍♂️	man supervillain: medium-light skin tone	People & Body	person-fantasy
🦹🏽‍♂️	man supervillain: medium skin tone	People & Body	person-fantasy
🦹🏾‍♂️	man supervillain: medium-dark skin tone	People & Body	person-fantasy
🦹🏿‍♂️	man supervillain: dark skin tone	People & Body	person-fantasy
🦹‍♀️	woman supervillain	People & Body	person-fantasy
🦹🏻‍♀️	woman supervillain: light skin tone	People & Body	person-fantasy
🦹🏼‍♀️	woman supervillain: medium-light skin tone	People & Body	person-fantasy
🦹🏽‍♀️	woman supervillain: medium skin tone	People & Body	person-fantasy
🦹🏾‍♀️	woman supervillain: medium-dark skin tone	People & Body	person-fantasy
🦹🏿‍♀️	woman supervillain: dark skin tone	People & Body	person-fantasy
🧙	mage	People & Body	person-fantasy
🧙🏻	mage: light skin tone	People & Body	person-fantasy
🧙🏼	mage: medium-light skin tone	People & Body	person-fantasy
🧙🏽	mage: medium skin tone	People & Body	person-fantasy
🧙🏾	mage: medium-dark skin tone	People & Body	person-fantasy
🧙🏿	mage: dark skin tone	People & Body	person-fantasy
🧙‍♂️	man mage	People & Body	person-fantasy
🧙🏻‍♂️	man mage: light skin tone	People & Body	person-fantasy
🧙🏼‍♂️	man mage: medium-light skin tone	People & Body	person-fantasy
🧙🏽‍♂️	man mage: medium skin tone	People & Body	person-fantasy
🧙🏾‍♂️	man mage: medium-dark skin tone	People & Body	person-fantasy
🧙🏿‍♂️	man mage: dark skin tone	People & Body	person-fantasy
🧙‍♀️	woman mage	People & Body	person-fantasy
🧙🏻‍♀️	woman mage: light skin tone	People & Body	person-fantasy
🧙🏼‍♀️	woman mage: medium-light skin tone	People & Body	person-fantasy
🧙🏽‍♀️	woman mage: medium skin tone	People & Body	person-fantasy
🧙🏾‍♀️	woman mage: medium-dark skin tone	People & Body	person-fantasy
🧙🏿‍♀️	woman mage: dark skin tone	People & Body	person-fantasy
🧚	fairy	People & Body	person-fantasy
🧚🏻	fairy: light skin tone	People & Body	person-fantasy
🧚🏼	fairy: medium-light skin tone	People & Body	person-fantasy
🧚🏽	fairy: medium skin tone	People & Body	person-fantasy
🧚🏾	fairy: medium-dark skin tone	People & Body	person-fantasy
🧚🏿	fairy: dark skin tone	People & Body	person-fantasy
🧚‍♂️	man fairy	People & Body	person-fantasy
🧚🏻‍♂️	man fairy: light skin tone	People & Body	person-fantasy
🧚🏼‍♂️	man fairy: medium-light skin tone	People & Body	person-fantasy
🧚🏽‍♂️	man fairy: medium skin tone	People & Body	person-fantasy
🧚🏾‍♂️	man fairy: medium-dark skin tone	People & Body	person-fantasy
🧚🏿‍♂️	man fairy: dark skin tone	People & Body	person-fantasy
🧚‍♀️	woman fairy	People & Body	person-fantasy
🧚🏻‍♀️	woman fairy: light skin tone	People & Body	person-fantasy
🧚🏼‍♀️	woman fairy: medium-light skin tone	People & Body	person-fantasy
🧚🏽‍♀️	woman fairy: medium skin tone	People & Body	person-fantasy
🧚🏾‍♀️	woman fairy: medium-dark skin tone	People & Body	person-fantasy
🧚🏿‍♀️	woman fairy: dark skin tone	People & Body	person-fantasy
🧛	vampire	People & Body	person-fantasy
🧛🏻	vampire: light skin tone	People & Body	person-fantasy
🧛🏼	vampire: medium-light skin tone	People & Body	person-fantasy
🧛🏽	vampire: medium skin tone	People & Body	person-fantasy
🧛🏾	vampire: medium-dark skin tone	People & Body	person-fantasy
🧛🏿	vampire: dark skin tone	People & Body	person-fantasy
🧛‍♂️	man vampire	People & Body	person-fantasy
🧛🏻‍♂️	man vampire: light skin tone	People & Body	person-fantasy
🧛🏼‍♂️	man vampire: medium-light skin tone	People & Body	person-fantasy
🧛🏽‍♂️	man vampire: medium skin tone	People & Body	person-fantasy
🧛🏾‍♂️	man vampire: medium-dark skin tone	People & Body	person-fantasy
🧛🏿‍♂️	man vampire: dark skin tone	People & Body	person-fantasy
🧛‍♀️	woman vampire	People & Body	person-fantasy
🧛🏻‍♀️	woman vampire: light skin tone	People & Body	person-fantasy
🧛🏼‍♀️	woman vampire: medium-light skin tone	People & Body	person-fantasy
🧛🏽‍♀️	woman vampire: medium skin tone	People & Body	person-fantasy
🧛🏾‍♀️	woman vampire: medium-dark skin tone	People & Body	person-fantasy
🧛🏿‍♀️	woman vampire: dark skin tone	People & Body	person-fantasy
🧜	merperson	People & Body	person-fantasy
🧜🏻	merperson: light skin tone	People & Body	person-fantasy
🧜🏼	merperson: medium-light skin tone	People & Body	person-fantasy
🧜🏽	merperson: medium skin tone	People & Body	person-fantasy
🧜🏾	merperson: medium-dark skin tone	People & Body	person-fantasy
🧜🏿	merperson: dark skin tone	People & Body	person-fantasy
🧜‍♂️	merman	People & Body	person-fantasy
🧜🏻‍♂️	merman: light skin tone	People & Body	person-fantasy
🧜🏼‍♂️	merman: medium-light skin tone	People & Body	person-fantasy
🧜🏽‍♂️	merman: medium skin tone	People & Body	person-fantasy
🧜🏾‍♂️	merman: medium-dark skin tone	People & Body	person-fantasy
🧜🏿‍♂️	merman: dark skin tone	People & Body	person-fantasy
🧜‍♀️	mermaid	People & Body	person-fantasy
🧜🏻‍♀️	mermaid: light skin tone	People & Body	person-fantasy
🧜🏼‍♀️	mermaid: medium-light skin tone	People & Body	person-fantasy
🧜🏽‍♀️	mermaid: medium skin tone	People & Body	person-fantasy
🧜🏾‍♀️	mermaid: medium-dark skin tone	People & Body	person-fantasy
🧜🏿‍♀️	mermaid: dark skin tone	People & Body	person-fantasy
🧝	elf	People & Body	person-fantasy
🧝🏻	elf: light skin tone	People & Body	person-fantasy
🧝🏼	elf: medium-light skin tone	People & Body	person-fantasy
🧝🏽	elf: medium skin tone	People & Body	person-fantasy
🧝🏾	elf: medium-dark skin tone	People & Body	person-fantasy
🧝🏿	elf: dark skin tone	People & Body	person-fantasy
🧝‍♂️	man elf	People & Body	person-fantasy
🧝🏻‍♂️	man elf: light skin tone	People & Body	person-fantasy
🧝🏼‍♂️	man elf: medium-light skin tone	People & Body	person-fantasy
🧝🏽‍♂️	man elf: medium skin tone	People & Body	person-fantasy
🧝🏾‍♂️	man elf: medium-dark skin tone	People & Body	person-fantasy
🧝🏿‍♂️	man elf: dark skin tone	People & Body	person-fantasy
🧝‍♀️	woman elf	People & Body	person-fantasy
🧝🏻‍♀️	woman elf: light skin tone	People & Body	person-fantasy
🧝🏼‍♀️	woman elf: medium-light skin tone	People & Body	person-fantasy
🧝🏽‍♀️	woman elf: medium skin tone	People & Body	person-fantasy
🧝🏾‍♀️	woman elf: medium-dark skin tone	People & Body	person-fantasy
🧝🏿‍♀️	woman elf: dark skin tone	People & Body	person-fantasy
🧞	genie	People & Body	person-fantasy
🧞‍♂️	man genie	People & Body	person-fantasy
🧞‍♀️	woman genie	People & Body	person-fantasy
🧟	zombie	People & Body	person-fantasy
🧟‍♂️	man zombie	People & Body	person-fantasy
🧟‍♀️	woman zombie	People & Body	person-fantasy
🧌	troll	People & Body	person-fantasy
🫈	hairy creature	People & Body	person-fantasy
💆	person getting massage	People & Body	person-activity
💆🏻	person getting massage: light skin tone	People & Body	person-activity
💆🏼	person getting massage: medium-light skin tone	People & Body	person-activity
💆🏽	person getting massage: medium skin tone	People & Body	person-activity
💆🏾	person getting massage: medium-dark skin tone	People & Body	person-activity
💆🏿	person getting massage: dark skin tone	People & Body	person-activity
💆‍♂️	man getting massage	People & Body	person-activity
💆🏻‍♂️	man getting massage: light skin tone	People & Body	person-activity
💆🏼‍♂️	man getting massage: medium-light skin tone	People & Body	person-activity
💆🏽‍♂️	man getting massage: medium skin tone	People & Body	person-activity
💆🏾‍♂️	man getting massage: medium-dark skin tone	People & Body	person-activity
💆🏿‍♂️	man getting massage: dark skin tone	People & Body	person-activity
💆‍♀️	woman getting massage	People & Body	person-activity
💆🏻‍♀️	woman getting massage: light skin tone	People & Body	person-activity
💆🏼‍♀️	woman getting massage: medium-light skin tone	People & Body	person-activity
💆🏽‍♀️	woman getting massage: medium skin tone	People & Body	person-activity
💆🏾‍♀️	woman getting massage: medium-dark skin tone	People & Body	person-activity
💆🏿‍♀️	woman getting massage: dark skin tone	People & Body	person-activity
💇	person getting haircut	People & Body	person-activity
💇🏻	person getting haircut: light skin tone	People & Body	person-activity
💇🏼	person getting haircut: medium-light skin tone	People & Body	person-activity
💇🏽	person getting haircut: medium skin tone	People & Body	person-activity
💇🏾	person getting haircut: medium-dark skin tone	People & Body	person-activity
💇🏿	person getting haircut: dark skin tone	People & Body	person-activity
💇‍♂️	man getting haircut	People & Body	person-activity
💇🏻‍♂️	man getting haircut: light skin tone	People & Body	person-activity
"""

private const val EMOJI_DATA_2 = """
💇🏼‍♂️	man getting haircut: medium-light skin tone	People & Body	person-activity
💇🏽‍♂️	man getting haircut: medium skin tone	People & Body	person-activity
💇🏾‍♂️	man getting haircut: medium-dark skin tone	People & Body	person-activity
💇🏿‍♂️	man getting haircut: dark skin tone	People & Body	person-activity
💇‍♀️	woman getting haircut	People & Body	person-activity
💇🏻‍♀️	woman getting haircut: light skin tone	People & Body	person-activity
💇🏼‍♀️	woman getting haircut: medium-light skin tone	People & Body	person-activity
💇🏽‍♀️	woman getting haircut: medium skin tone	People & Body	person-activity
💇🏾‍♀️	woman getting haircut: medium-dark skin tone	People & Body	person-activity
💇🏿‍♀️	woman getting haircut: dark skin tone	People & Body	person-activity
🚶	person walking	People & Body	person-activity
🚶🏻	person walking: light skin tone	People & Body	person-activity
🚶🏼	person walking: medium-light skin tone	People & Body	person-activity
🚶🏽	person walking: medium skin tone	People & Body	person-activity
🚶🏾	person walking: medium-dark skin tone	People & Body	person-activity
🚶🏿	person walking: dark skin tone	People & Body	person-activity
🚶‍♂️	man walking	People & Body	person-activity
🚶🏻‍♂️	man walking: light skin tone	People & Body	person-activity
🚶🏼‍♂️	man walking: medium-light skin tone	People & Body	person-activity
🚶🏽‍♂️	man walking: medium skin tone	People & Body	person-activity
🚶🏾‍♂️	man walking: medium-dark skin tone	People & Body	person-activity
🚶🏿‍♂️	man walking: dark skin tone	People & Body	person-activity
🚶‍♀️	woman walking	People & Body	person-activity
🚶🏻‍♀️	woman walking: light skin tone	People & Body	person-activity
🚶🏼‍♀️	woman walking: medium-light skin tone	People & Body	person-activity
🚶🏽‍♀️	woman walking: medium skin tone	People & Body	person-activity
🚶🏾‍♀️	woman walking: medium-dark skin tone	People & Body	person-activity
🚶🏿‍♀️	woman walking: dark skin tone	People & Body	person-activity
🚶‍➡️	person walking facing right	People & Body	person-activity
🚶🏻‍➡️	person walking facing right: light skin tone	People & Body	person-activity
🚶🏼‍➡️	person walking facing right: medium-light skin tone	People & Body	person-activity
🚶🏽‍➡️	person walking facing right: medium skin tone	People & Body	person-activity
🚶🏾‍➡️	person walking facing right: medium-dark skin tone	People & Body	person-activity
🚶🏿‍➡️	person walking facing right: dark skin tone	People & Body	person-activity
🚶‍♀️‍➡️	woman walking facing right	People & Body	person-activity
🚶🏻‍♀️‍➡️	woman walking facing right: light skin tone	People & Body	person-activity
🚶🏼‍♀️‍➡️	woman walking facing right: medium-light skin tone	People & Body	person-activity
🚶🏽‍♀️‍➡️	woman walking facing right: medium skin tone	People & Body	person-activity
🚶🏾‍♀️‍➡️	woman walking facing right: medium-dark skin tone	People & Body	person-activity
🚶🏿‍♀️‍➡️	woman walking facing right: dark skin tone	People & Body	person-activity
🚶‍♂️‍➡️	man walking facing right	People & Body	person-activity
🚶🏻‍♂️‍➡️	man walking facing right: light skin tone	People & Body	person-activity
🚶🏼‍♂️‍➡️	man walking facing right: medium-light skin tone	People & Body	person-activity
🚶🏽‍♂️‍➡️	man walking facing right: medium skin tone	People & Body	person-activity
🚶🏾‍♂️‍➡️	man walking facing right: medium-dark skin tone	People & Body	person-activity
🚶🏿‍♂️‍➡️	man walking facing right: dark skin tone	People & Body	person-activity
🧍	person standing	People & Body	person-activity
🧍🏻	person standing: light skin tone	People & Body	person-activity
🧍🏼	person standing: medium-light skin tone	People & Body	person-activity
🧍🏽	person standing: medium skin tone	People & Body	person-activity
🧍🏾	person standing: medium-dark skin tone	People & Body	person-activity
🧍🏿	person standing: dark skin tone	People & Body	person-activity
🧍‍♂️	man standing	People & Body	person-activity
🧍🏻‍♂️	man standing: light skin tone	People & Body	person-activity
🧍🏼‍♂️	man standing: medium-light skin tone	People & Body	person-activity
🧍🏽‍♂️	man standing: medium skin tone	People & Body	person-activity
🧍🏾‍♂️	man standing: medium-dark skin tone	People & Body	person-activity
🧍🏿‍♂️	man standing: dark skin tone	People & Body	person-activity
🧍‍♀️	woman standing	People & Body	person-activity
🧍🏻‍♀️	woman standing: light skin tone	People & Body	person-activity
🧍🏼‍♀️	woman standing: medium-light skin tone	People & Body	person-activity
🧍🏽‍♀️	woman standing: medium skin tone	People & Body	person-activity
🧍🏾‍♀️	woman standing: medium-dark skin tone	People & Body	person-activity
🧍🏿‍♀️	woman standing: dark skin tone	People & Body	person-activity
🧎	person kneeling	People & Body	person-activity
🧎🏻	person kneeling: light skin tone	People & Body	person-activity
🧎🏼	person kneeling: medium-light skin tone	People & Body	person-activity
🧎🏽	person kneeling: medium skin tone	People & Body	person-activity
🧎🏾	person kneeling: medium-dark skin tone	People & Body	person-activity
🧎🏿	person kneeling: dark skin tone	People & Body	person-activity
🧎‍♂️	man kneeling	People & Body	person-activity
🧎🏻‍♂️	man kneeling: light skin tone	People & Body	person-activity
🧎🏼‍♂️	man kneeling: medium-light skin tone	People & Body	person-activity
🧎🏽‍♂️	man kneeling: medium skin tone	People & Body	person-activity
🧎🏾‍♂️	man kneeling: medium-dark skin tone	People & Body	person-activity
🧎🏿‍♂️	man kneeling: dark skin tone	People & Body	person-activity
🧎‍♀️	woman kneeling	People & Body	person-activity
🧎🏻‍♀️	woman kneeling: light skin tone	People & Body	person-activity
🧎🏼‍♀️	woman kneeling: medium-light skin tone	People & Body	person-activity
🧎🏽‍♀️	woman kneeling: medium skin tone	People & Body	person-activity
🧎🏾‍♀️	woman kneeling: medium-dark skin tone	People & Body	person-activity
🧎🏿‍♀️	woman kneeling: dark skin tone	People & Body	person-activity
🧎‍➡️	person kneeling facing right	People & Body	person-activity
🧎🏻‍➡️	person kneeling facing right: light skin tone	People & Body	person-activity
🧎🏼‍➡️	person kneeling facing right: medium-light skin tone	People & Body	person-activity
🧎🏽‍➡️	person kneeling facing right: medium skin tone	People & Body	person-activity
🧎🏾‍➡️	person kneeling facing right: medium-dark skin tone	People & Body	person-activity
🧎🏿‍➡️	person kneeling facing right: dark skin tone	People & Body	person-activity
🧎‍♀️‍➡️	woman kneeling facing right	People & Body	person-activity
🧎🏻‍♀️‍➡️	woman kneeling facing right: light skin tone	People & Body	person-activity
🧎🏼‍♀️‍➡️	woman kneeling facing right: medium-light skin tone	People & Body	person-activity
🧎🏽‍♀️‍➡️	woman kneeling facing right: medium skin tone	People & Body	person-activity
🧎🏾‍♀️‍➡️	woman kneeling facing right: medium-dark skin tone	People & Body	person-activity
🧎🏿‍♀️‍➡️	woman kneeling facing right: dark skin tone	People & Body	person-activity
🧎‍♂️‍➡️	man kneeling facing right	People & Body	person-activity
🧎🏻‍♂️‍➡️	man kneeling facing right: light skin tone	People & Body	person-activity
🧎🏼‍♂️‍➡️	man kneeling facing right: medium-light skin tone	People & Body	person-activity
🧎🏽‍♂️‍➡️	man kneeling facing right: medium skin tone	People & Body	person-activity
🧎🏾‍♂️‍➡️	man kneeling facing right: medium-dark skin tone	People & Body	person-activity
🧎🏿‍♂️‍➡️	man kneeling facing right: dark skin tone	People & Body	person-activity
🧑‍🦯	person with white cane	People & Body	person-activity
🧑🏻‍🦯	person with white cane: light skin tone	People & Body	person-activity
🧑🏼‍🦯	person with white cane: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦯	person with white cane: medium skin tone	People & Body	person-activity
🧑🏾‍🦯	person with white cane: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦯	person with white cane: dark skin tone	People & Body	person-activity
🧑‍🦯‍➡️	person with white cane facing right	People & Body	person-activity
🧑🏻‍🦯‍➡️	person with white cane facing right: light skin tone	People & Body	person-activity
🧑🏼‍🦯‍➡️	person with white cane facing right: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦯‍➡️	person with white cane facing right: medium skin tone	People & Body	person-activity
🧑🏾‍🦯‍➡️	person with white cane facing right: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦯‍➡️	person with white cane facing right: dark skin tone	People & Body	person-activity
👨‍🦯	man with white cane	People & Body	person-activity
👨🏻‍🦯	man with white cane: light skin tone	People & Body	person-activity
👨🏼‍🦯	man with white cane: medium-light skin tone	People & Body	person-activity
👨🏽‍🦯	man with white cane: medium skin tone	People & Body	person-activity
👨🏾‍🦯	man with white cane: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦯	man with white cane: dark skin tone	People & Body	person-activity
👨‍🦯‍➡️	man with white cane facing right	People & Body	person-activity
👨🏻‍🦯‍➡️	man with white cane facing right: light skin tone	People & Body	person-activity
👨🏼‍🦯‍➡️	man with white cane facing right: medium-light skin tone	People & Body	person-activity
👨🏽‍🦯‍➡️	man with white cane facing right: medium skin tone	People & Body	person-activity
👨🏾‍🦯‍➡️	man with white cane facing right: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦯‍➡️	man with white cane facing right: dark skin tone	People & Body	person-activity
👩‍🦯	woman with white cane	People & Body	person-activity
👩🏻‍🦯	woman with white cane: light skin tone	People & Body	person-activity
👩🏼‍🦯	woman with white cane: medium-light skin tone	People & Body	person-activity
👩🏽‍🦯	woman with white cane: medium skin tone	People & Body	person-activity
👩🏾‍🦯	woman with white cane: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦯	woman with white cane: dark skin tone	People & Body	person-activity
👩‍🦯‍➡️	woman with white cane facing right	People & Body	person-activity
👩🏻‍🦯‍➡️	woman with white cane facing right: light skin tone	People & Body	person-activity
👩🏼‍🦯‍➡️	woman with white cane facing right: medium-light skin tone	People & Body	person-activity
👩🏽‍🦯‍➡️	woman with white cane facing right: medium skin tone	People & Body	person-activity
👩🏾‍🦯‍➡️	woman with white cane facing right: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦯‍➡️	woman with white cane facing right: dark skin tone	People & Body	person-activity
🧑‍🦼	person in motorized wheelchair	People & Body	person-activity
🧑🏻‍🦼	person in motorized wheelchair: light skin tone	People & Body	person-activity
🧑🏼‍🦼	person in motorized wheelchair: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦼	person in motorized wheelchair: medium skin tone	People & Body	person-activity
🧑🏾‍🦼	person in motorized wheelchair: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦼	person in motorized wheelchair: dark skin tone	People & Body	person-activity
🧑‍🦼‍➡️	person in motorized wheelchair facing right	People & Body	person-activity
🧑🏻‍🦼‍➡️	person in motorized wheelchair facing right: light skin tone	People & Body	person-activity
🧑🏼‍🦼‍➡️	person in motorized wheelchair facing right: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦼‍➡️	person in motorized wheelchair facing right: medium skin tone	People & Body	person-activity
🧑🏾‍🦼‍➡️	person in motorized wheelchair facing right: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦼‍➡️	person in motorized wheelchair facing right: dark skin tone	People & Body	person-activity
👨‍🦼	man in motorized wheelchair	People & Body	person-activity
👨🏻‍🦼	man in motorized wheelchair: light skin tone	People & Body	person-activity
👨🏼‍🦼	man in motorized wheelchair: medium-light skin tone	People & Body	person-activity
👨🏽‍🦼	man in motorized wheelchair: medium skin tone	People & Body	person-activity
👨🏾‍🦼	man in motorized wheelchair: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦼	man in motorized wheelchair: dark skin tone	People & Body	person-activity
👨‍🦼‍➡️	man in motorized wheelchair facing right	People & Body	person-activity
👨🏻‍🦼‍➡️	man in motorized wheelchair facing right: light skin tone	People & Body	person-activity
👨🏼‍🦼‍➡️	man in motorized wheelchair facing right: medium-light skin tone	People & Body	person-activity
👨🏽‍🦼‍➡️	man in motorized wheelchair facing right: medium skin tone	People & Body	person-activity
👨🏾‍🦼‍➡️	man in motorized wheelchair facing right: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦼‍➡️	man in motorized wheelchair facing right: dark skin tone	People & Body	person-activity
👩‍🦼	woman in motorized wheelchair	People & Body	person-activity
👩🏻‍🦼	woman in motorized wheelchair: light skin tone	People & Body	person-activity
👩🏼‍🦼	woman in motorized wheelchair: medium-light skin tone	People & Body	person-activity
👩🏽‍🦼	woman in motorized wheelchair: medium skin tone	People & Body	person-activity
👩🏾‍🦼	woman in motorized wheelchair: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦼	woman in motorized wheelchair: dark skin tone	People & Body	person-activity
👩‍🦼‍➡️	woman in motorized wheelchair facing right	People & Body	person-activity
👩🏻‍🦼‍➡️	woman in motorized wheelchair facing right: light skin tone	People & Body	person-activity
👩🏼‍🦼‍➡️	woman in motorized wheelchair facing right: medium-light skin tone	People & Body	person-activity
👩🏽‍🦼‍➡️	woman in motorized wheelchair facing right: medium skin tone	People & Body	person-activity
👩🏾‍🦼‍➡️	woman in motorized wheelchair facing right: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦼‍➡️	woman in motorized wheelchair facing right: dark skin tone	People & Body	person-activity
🧑‍🦽	person in manual wheelchair	People & Body	person-activity
🧑🏻‍🦽	person in manual wheelchair: light skin tone	People & Body	person-activity
🧑🏼‍🦽	person in manual wheelchair: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦽	person in manual wheelchair: medium skin tone	People & Body	person-activity
🧑🏾‍🦽	person in manual wheelchair: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦽	person in manual wheelchair: dark skin tone	People & Body	person-activity
🧑‍🦽‍➡️	person in manual wheelchair facing right	People & Body	person-activity
🧑🏻‍🦽‍➡️	person in manual wheelchair facing right: light skin tone	People & Body	person-activity
🧑🏼‍🦽‍➡️	person in manual wheelchair facing right: medium-light skin tone	People & Body	person-activity
🧑🏽‍🦽‍➡️	person in manual wheelchair facing right: medium skin tone	People & Body	person-activity
🧑🏾‍🦽‍➡️	person in manual wheelchair facing right: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🦽‍➡️	person in manual wheelchair facing right: dark skin tone	People & Body	person-activity
👨‍🦽	man in manual wheelchair	People & Body	person-activity
👨🏻‍🦽	man in manual wheelchair: light skin tone	People & Body	person-activity
👨🏼‍🦽	man in manual wheelchair: medium-light skin tone	People & Body	person-activity
👨🏽‍🦽	man in manual wheelchair: medium skin tone	People & Body	person-activity
👨🏾‍🦽	man in manual wheelchair: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦽	man in manual wheelchair: dark skin tone	People & Body	person-activity
👨‍🦽‍➡️	man in manual wheelchair facing right	People & Body	person-activity
👨🏻‍🦽‍➡️	man in manual wheelchair facing right: light skin tone	People & Body	person-activity
👨🏼‍🦽‍➡️	man in manual wheelchair facing right: medium-light skin tone	People & Body	person-activity
👨🏽‍🦽‍➡️	man in manual wheelchair facing right: medium skin tone	People & Body	person-activity
👨🏾‍🦽‍➡️	man in manual wheelchair facing right: medium-dark skin tone	People & Body	person-activity
👨🏿‍🦽‍➡️	man in manual wheelchair facing right: dark skin tone	People & Body	person-activity
👩‍🦽	woman in manual wheelchair	People & Body	person-activity
👩🏻‍🦽	woman in manual wheelchair: light skin tone	People & Body	person-activity
👩🏼‍🦽	woman in manual wheelchair: medium-light skin tone	People & Body	person-activity
👩🏽‍🦽	woman in manual wheelchair: medium skin tone	People & Body	person-activity
👩🏾‍🦽	woman in manual wheelchair: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦽	woman in manual wheelchair: dark skin tone	People & Body	person-activity
👩‍🦽‍➡️	woman in manual wheelchair facing right	People & Body	person-activity
👩🏻‍🦽‍➡️	woman in manual wheelchair facing right: light skin tone	People & Body	person-activity
👩🏼‍🦽‍➡️	woman in manual wheelchair facing right: medium-light skin tone	People & Body	person-activity
👩🏽‍🦽‍➡️	woman in manual wheelchair facing right: medium skin tone	People & Body	person-activity
👩🏾‍🦽‍➡️	woman in manual wheelchair facing right: medium-dark skin tone	People & Body	person-activity
👩🏿‍🦽‍➡️	woman in manual wheelchair facing right: dark skin tone	People & Body	person-activity
🏃	person running	People & Body	person-activity
🏃🏻	person running: light skin tone	People & Body	person-activity
🏃🏼	person running: medium-light skin tone	People & Body	person-activity
🏃🏽	person running: medium skin tone	People & Body	person-activity
🏃🏾	person running: medium-dark skin tone	People & Body	person-activity
🏃🏿	person running: dark skin tone	People & Body	person-activity
🏃‍♂️	man running	People & Body	person-activity
🏃🏻‍♂️	man running: light skin tone	People & Body	person-activity
🏃🏼‍♂️	man running: medium-light skin tone	People & Body	person-activity
🏃🏽‍♂️	man running: medium skin tone	People & Body	person-activity
🏃🏾‍♂️	man running: medium-dark skin tone	People & Body	person-activity
🏃🏿‍♂️	man running: dark skin tone	People & Body	person-activity
🏃‍♀️	woman running	People & Body	person-activity
🏃🏻‍♀️	woman running: light skin tone	People & Body	person-activity
🏃🏼‍♀️	woman running: medium-light skin tone	People & Body	person-activity
🏃🏽‍♀️	woman running: medium skin tone	People & Body	person-activity
🏃🏾‍♀️	woman running: medium-dark skin tone	People & Body	person-activity
🏃🏿‍♀️	woman running: dark skin tone	People & Body	person-activity
🏃‍➡️	person running facing right	People & Body	person-activity
🏃🏻‍➡️	person running facing right: light skin tone	People & Body	person-activity
🏃🏼‍➡️	person running facing right: medium-light skin tone	People & Body	person-activity
🏃🏽‍➡️	person running facing right: medium skin tone	People & Body	person-activity
🏃🏾‍➡️	person running facing right: medium-dark skin tone	People & Body	person-activity
🏃🏿‍➡️	person running facing right: dark skin tone	People & Body	person-activity
🏃‍♀️‍➡️	woman running facing right	People & Body	person-activity
🏃🏻‍♀️‍➡️	woman running facing right: light skin tone	People & Body	person-activity
🏃🏼‍♀️‍➡️	woman running facing right: medium-light skin tone	People & Body	person-activity
🏃🏽‍♀️‍➡️	woman running facing right: medium skin tone	People & Body	person-activity
🏃🏾‍♀️‍➡️	woman running facing right: medium-dark skin tone	People & Body	person-activity
🏃🏿‍♀️‍➡️	woman running facing right: dark skin tone	People & Body	person-activity
🏃‍♂️‍➡️	man running facing right	People & Body	person-activity
🏃🏻‍♂️‍➡️	man running facing right: light skin tone	People & Body	person-activity
🏃🏼‍♂️‍➡️	man running facing right: medium-light skin tone	People & Body	person-activity
🏃🏽‍♂️‍➡️	man running facing right: medium skin tone	People & Body	person-activity
🏃🏾‍♂️‍➡️	man running facing right: medium-dark skin tone	People & Body	person-activity
🏃🏿‍♂️‍➡️	man running facing right: dark skin tone	People & Body	person-activity
🧑‍🩰	ballet dancer	People & Body	person-activity
🧑🏻‍🩰	ballet dancer: light skin tone	People & Body	person-activity
🧑🏼‍🩰	ballet dancer: medium-light skin tone	People & Body	person-activity
🧑🏽‍🩰	ballet dancer: medium skin tone	People & Body	person-activity
🧑🏾‍🩰	ballet dancer: medium-dark skin tone	People & Body	person-activity
🧑🏿‍🩰	ballet dancer: dark skin tone	People & Body	person-activity
💃	woman dancing	People & Body	person-activity
💃🏻	woman dancing: light skin tone	People & Body	person-activity
💃🏼	woman dancing: medium-light skin tone	People & Body	person-activity
💃🏽	woman dancing: medium skin tone	People & Body	person-activity
💃🏾	woman dancing: medium-dark skin tone	People & Body	person-activity
💃🏿	woman dancing: dark skin tone	People & Body	person-activity
🕺	man dancing	People & Body	person-activity
🕺🏻	man dancing: light skin tone	People & Body	person-activity
🕺🏼	man dancing: medium-light skin tone	People & Body	person-activity
🕺🏽	man dancing: medium skin tone	People & Body	person-activity
🕺🏾	man dancing: medium-dark skin tone	People & Body	person-activity
🕺🏿	man dancing: dark skin tone	People & Body	person-activity
🕴️	person in suit levitating	People & Body	person-activity
🕴🏻	person in suit levitating: light skin tone	People & Body	person-activity
🕴🏼	person in suit levitating: medium-light skin tone	People & Body	person-activity
🕴🏽	person in suit levitating: medium skin tone	People & Body	person-activity
🕴🏾	person in suit levitating: medium-dark skin tone	People & Body	person-activity
🕴🏿	person in suit levitating: dark skin tone	People & Body	person-activity
👯	people with bunny ears	People & Body	person-activity
👯🏻	people with bunny ears: light skin tone	People & Body	person-activity
👯🏼	people with bunny ears: medium-light skin tone	People & Body	person-activity
👯🏽	people with bunny ears: medium skin tone	People & Body	person-activity
👯🏾	people with bunny ears: medium-dark skin tone	People & Body	person-activity
👯🏿	people with bunny ears: dark skin tone	People & Body	person-activity
👯‍♂️	men with bunny ears	People & Body	person-activity
👯🏻‍♂️	men with bunny ears: light skin tone	People & Body	person-activity
👯🏼‍♂️	men with bunny ears: medium-light skin tone	People & Body	person-activity
👯🏽‍♂️	men with bunny ears: medium skin tone	People & Body	person-activity
👯🏾‍♂️	men with bunny ears: medium-dark skin tone	People & Body	person-activity
👯🏿‍♂️	men with bunny ears: dark skin tone	People & Body	person-activity
👯‍♀️	women with bunny ears	People & Body	person-activity
👯🏻‍♀️	women with bunny ears: light skin tone	People & Body	person-activity
👯🏼‍♀️	women with bunny ears: medium-light skin tone	People & Body	person-activity
👯🏽‍♀️	women with bunny ears: medium skin tone	People & Body	person-activity
👯🏾‍♀️	women with bunny ears: medium-dark skin tone	People & Body	person-activity
👯🏿‍♀️	women with bunny ears: dark skin tone	People & Body	person-activity
🧑🏻‍🐰‍🧑🏼	people with bunny ears: light skin tone, medium-light skin tone	People & Body	person-activity
🧑🏻‍🐰‍🧑🏽	people with bunny ears: light skin tone, medium skin tone	People & Body	person-activity
🧑🏻‍🐰‍🧑🏾	people with bunny ears: light skin tone, medium-dark skin tone	People & Body	person-activity
🧑🏻‍🐰‍🧑🏿	people with bunny ears: light skin tone, dark skin tone	People & Body	person-activity
🧑🏼‍🐰‍🧑🏻	people with bunny ears: medium-light skin tone, light skin tone	People & Body	person-activity
🧑🏼‍🐰‍🧑🏽	people with bunny ears: medium-light skin tone, medium skin tone	People & Body	person-activity
🧑🏼‍🐰‍🧑🏾	people with bunny ears: medium-light skin tone, medium-dark skin tone	People & Body	person-activity
🧑🏼‍🐰‍🧑🏿	people with bunny ears: medium-light skin tone, dark skin tone	People & Body	person-activity
🧑🏽‍🐰‍🧑🏻	people with bunny ears: medium skin tone, light skin tone	People & Body	person-activity
🧑🏽‍🐰‍🧑🏼	people with bunny ears: medium skin tone, medium-light skin tone	People & Body	person-activity
🧑🏽‍🐰‍🧑🏾	people with bunny ears: medium skin tone, medium-dark skin tone	People & Body	person-activity
🧑🏽‍🐰‍🧑🏿	people with bunny ears: medium skin tone, dark skin tone	People & Body	person-activity
🧑🏾‍🐰‍🧑🏻	people with bunny ears: medium-dark skin tone, light skin tone	People & Body	person-activity
🧑🏾‍🐰‍🧑🏼	people with bunny ears: medium-dark skin tone, medium-light skin tone	People & Body	person-activity
🧑🏾‍🐰‍🧑🏽	people with bunny ears: medium-dark skin tone, medium skin tone	People & Body	person-activity
🧑🏾‍🐰‍🧑🏿	people with bunny ears: medium-dark skin tone, dark skin tone	People & Body	person-activity
🧑🏿‍🐰‍🧑🏻	people with bunny ears: dark skin tone, light skin tone	People & Body	person-activity
🧑🏿‍🐰‍🧑🏼	people with bunny ears: dark skin tone, medium-light skin tone	People & Body	person-activity
🧑🏿‍🐰‍🧑🏽	people with bunny ears: dark skin tone, medium skin tone	People & Body	person-activity
🧑🏿‍🐰‍🧑🏾	people with bunny ears: dark skin tone, medium-dark skin tone	People & Body	person-activity
👨🏻‍🐰‍👨🏼	men with bunny ears: light skin tone, medium-light skin tone	People & Body	person-activity
👨🏻‍🐰‍👨🏽	men with bunny ears: light skin tone, medium skin tone	People & Body	person-activity
👨🏻‍🐰‍👨🏾	men with bunny ears: light skin tone, medium-dark skin tone	People & Body	person-activity
👨🏻‍🐰‍👨🏿	men with bunny ears: light skin tone, dark skin tone	People & Body	person-activity
👨🏼‍🐰‍👨🏻	men with bunny ears: medium-light skin tone, light skin tone	People & Body	person-activity
👨🏼‍🐰‍👨🏽	men with bunny ears: medium-light skin tone, medium skin tone	People & Body	person-activity
👨🏼‍🐰‍👨🏾	men with bunny ears: medium-light skin tone, medium-dark skin tone	People & Body	person-activity
👨🏼‍🐰‍👨🏿	men with bunny ears: medium-light skin tone, dark skin tone	People & Body	person-activity
👨🏽‍🐰‍👨🏻	men with bunny ears: medium skin tone, light skin tone	People & Body	person-activity
👨🏽‍🐰‍👨🏼	men with bunny ears: medium skin tone, medium-light skin tone	People & Body	person-activity
👨🏽‍🐰‍👨🏾	men with bunny ears: medium skin tone, medium-dark skin tone	People & Body	person-activity
👨🏽‍🐰‍👨🏿	men with bunny ears: medium skin tone, dark skin tone	People & Body	person-activity
👨🏾‍🐰‍👨🏻	men with bunny ears: medium-dark skin tone, light skin tone	People & Body	person-activity
👨🏾‍🐰‍👨🏼	men with bunny ears: medium-dark skin tone, medium-light skin tone	People & Body	person-activity
👨🏾‍🐰‍👨🏽	men with bunny ears: medium-dark skin tone, medium skin tone	People & Body	person-activity
👨🏾‍🐰‍👨🏿	men with bunny ears: medium-dark skin tone, dark skin tone	People & Body	person-activity
👨🏿‍🐰‍👨🏻	men with bunny ears: dark skin tone, light skin tone	People & Body	person-activity
👨🏿‍🐰‍👨🏼	men with bunny ears: dark skin tone, medium-light skin tone	People & Body	person-activity
👨🏿‍🐰‍👨🏽	men with bunny ears: dark skin tone, medium skin tone	People & Body	person-activity
👨🏿‍🐰‍👨🏾	men with bunny ears: dark skin tone, medium-dark skin tone	People & Body	person-activity
👩🏻‍🐰‍👩🏼	women with bunny ears: light skin tone, medium-light skin tone	People & Body	person-activity
👩🏻‍🐰‍👩🏽	women with bunny ears: light skin tone, medium skin tone	People & Body	person-activity
👩🏻‍🐰‍👩🏾	women with bunny ears: light skin tone, medium-dark skin tone	People & Body	person-activity
👩🏻‍🐰‍👩🏿	women with bunny ears: light skin tone, dark skin tone	People & Body	person-activity
👩🏼‍🐰‍👩🏻	women with bunny ears: medium-light skin tone, light skin tone	People & Body	person-activity
👩🏼‍🐰‍👩🏽	women with bunny ears: medium-light skin tone, medium skin tone	People & Body	person-activity
👩🏼‍🐰‍👩🏾	women with bunny ears: medium-light skin tone, medium-dark skin tone	People & Body	person-activity
👩🏼‍🐰‍👩🏿	women with bunny ears: medium-light skin tone, dark skin tone	People & Body	person-activity
👩🏽‍🐰‍👩🏻	women with bunny ears: medium skin tone, light skin tone	People & Body	person-activity
👩🏽‍🐰‍👩🏼	women with bunny ears: medium skin tone, medium-light skin tone	People & Body	person-activity
👩🏽‍🐰‍👩🏾	women with bunny ears: medium skin tone, medium-dark skin tone	People & Body	person-activity
👩🏽‍🐰‍👩🏿	women with bunny ears: medium skin tone, dark skin tone	People & Body	person-activity
👩🏾‍🐰‍👩🏻	women with bunny ears: medium-dark skin tone, light skin tone	People & Body	person-activity
👩🏾‍🐰‍👩🏼	women with bunny ears: medium-dark skin tone, medium-light skin tone	People & Body	person-activity
👩🏾‍🐰‍👩🏽	women with bunny ears: medium-dark skin tone, medium skin tone	People & Body	person-activity
👩🏾‍🐰‍👩🏿	women with bunny ears: medium-dark skin tone, dark skin tone	People & Body	person-activity
👩🏿‍🐰‍👩🏻	women with bunny ears: dark skin tone, light skin tone	People & Body	person-activity
👩🏿‍🐰‍👩🏼	women with bunny ears: dark skin tone, medium-light skin tone	People & Body	person-activity
👩🏿‍🐰‍👩🏽	women with bunny ears: dark skin tone, medium skin tone	People & Body	person-activity
👩🏿‍🐰‍👩🏾	women with bunny ears: dark skin tone, medium-dark skin tone	People & Body	person-activity
🧖	person in steamy room	People & Body	person-activity
🧖🏻	person in steamy room: light skin tone	People & Body	person-activity
🧖🏼	person in steamy room: medium-light skin tone	People & Body	person-activity
🧖🏽	person in steamy room: medium skin tone	People & Body	person-activity
🧖🏾	person in steamy room: medium-dark skin tone	People & Body	person-activity
🧖🏿	person in steamy room: dark skin tone	People & Body	person-activity
🧖‍♂️	man in steamy room	People & Body	person-activity
🧖🏻‍♂️	man in steamy room: light skin tone	People & Body	person-activity
🧖🏼‍♂️	man in steamy room: medium-light skin tone	People & Body	person-activity
🧖🏽‍♂️	man in steamy room: medium skin tone	People & Body	person-activity
🧖🏾‍♂️	man in steamy room: medium-dark skin tone	People & Body	person-activity
🧖🏿‍♂️	man in steamy room: dark skin tone	People & Body	person-activity
🧖‍♀️	woman in steamy room	People & Body	person-activity
🧖🏻‍♀️	woman in steamy room: light skin tone	People & Body	person-activity
🧖🏼‍♀️	woman in steamy room: medium-light skin tone	People & Body	person-activity
🧖🏽‍♀️	woman in steamy room: medium skin tone	People & Body	person-activity
🧖🏾‍♀️	woman in steamy room: medium-dark skin tone	People & Body	person-activity
🧖🏿‍♀️	woman in steamy room: dark skin tone	People & Body	person-activity
🧗	person climbing	People & Body	person-activity
🧗🏻	person climbing: light skin tone	People & Body	person-activity
🧗🏼	person climbing: medium-light skin tone	People & Body	person-activity
🧗🏽	person climbing: medium skin tone	People & Body	person-activity
🧗🏾	person climbing: medium-dark skin tone	People & Body	person-activity
🧗🏿	person climbing: dark skin tone	People & Body	person-activity
🧗‍♂️	man climbing	People & Body	person-activity
🧗🏻‍♂️	man climbing: light skin tone	People & Body	person-activity
🧗🏼‍♂️	man climbing: medium-light skin tone	People & Body	person-activity
🧗🏽‍♂️	man climbing: medium skin tone	People & Body	person-activity
🧗🏾‍♂️	man climbing: medium-dark skin tone	People & Body	person-activity
🧗🏿‍♂️	man climbing: dark skin tone	People & Body	person-activity
🧗‍♀️	woman climbing	People & Body	person-activity
🧗🏻‍♀️	woman climbing: light skin tone	People & Body	person-activity
🧗🏼‍♀️	woman climbing: medium-light skin tone	People & Body	person-activity
🧗🏽‍♀️	woman climbing: medium skin tone	People & Body	person-activity
🧗🏾‍♀️	woman climbing: medium-dark skin tone	People & Body	person-activity
🧗🏿‍♀️	woman climbing: dark skin tone	People & Body	person-activity
🤺	person fencing	People & Body	person-sport
🏇	horse racing	People & Body	person-sport
🏇🏻	horse racing: light skin tone	People & Body	person-sport
🏇🏼	horse racing: medium-light skin tone	People & Body	person-sport
🏇🏽	horse racing: medium skin tone	People & Body	person-sport
🏇🏾	horse racing: medium-dark skin tone	People & Body	person-sport
🏇🏿	horse racing: dark skin tone	People & Body	person-sport
⛷️	skier	People & Body	person-sport
🏂	snowboarder	People & Body	person-sport
🏂🏻	snowboarder: light skin tone	People & Body	person-sport
🏂🏼	snowboarder: medium-light skin tone	People & Body	person-sport
🏂🏽	snowboarder: medium skin tone	People & Body	person-sport
🏂🏾	snowboarder: medium-dark skin tone	People & Body	person-sport
🏂🏿	snowboarder: dark skin tone	People & Body	person-sport
🏌️	person golfing	People & Body	person-sport
🏌🏻	person golfing: light skin tone	People & Body	person-sport
🏌🏼	person golfing: medium-light skin tone	People & Body	person-sport
🏌🏽	person golfing: medium skin tone	People & Body	person-sport
🏌🏾	person golfing: medium-dark skin tone	People & Body	person-sport
🏌🏿	person golfing: dark skin tone	People & Body	person-sport
🏌️‍♂️	man golfing	People & Body	person-sport
🏌🏻‍♂️	man golfing: light skin tone	People & Body	person-sport
🏌🏼‍♂️	man golfing: medium-light skin tone	People & Body	person-sport
🏌🏽‍♂️	man golfing: medium skin tone	People & Body	person-sport
🏌🏾‍♂️	man golfing: medium-dark skin tone	People & Body	person-sport
🏌🏿‍♂️	man golfing: dark skin tone	People & Body	person-sport
🏌️‍♀️	woman golfing	People & Body	person-sport
🏌🏻‍♀️	woman golfing: light skin tone	People & Body	person-sport
🏌🏼‍♀️	woman golfing: medium-light skin tone	People & Body	person-sport
🏌🏽‍♀️	woman golfing: medium skin tone	People & Body	person-sport
🏌🏾‍♀️	woman golfing: medium-dark skin tone	People & Body	person-sport
🏌🏿‍♀️	woman golfing: dark skin tone	People & Body	person-sport
🏄	person surfing	People & Body	person-sport
🏄🏻	person surfing: light skin tone	People & Body	person-sport
🏄🏼	person surfing: medium-light skin tone	People & Body	person-sport
🏄🏽	person surfing: medium skin tone	People & Body	person-sport
🏄🏾	person surfing: medium-dark skin tone	People & Body	person-sport
🏄🏿	person surfing: dark skin tone	People & Body	person-sport
🏄‍♂️	man surfing	People & Body	person-sport
🏄🏻‍♂️	man surfing: light skin tone	People & Body	person-sport
🏄🏼‍♂️	man surfing: medium-light skin tone	People & Body	person-sport
🏄🏽‍♂️	man surfing: medium skin tone	People & Body	person-sport
🏄🏾‍♂️	man surfing: medium-dark skin tone	People & Body	person-sport
🏄🏿‍♂️	man surfing: dark skin tone	People & Body	person-sport
🏄‍♀️	woman surfing	People & Body	person-sport
🏄🏻‍♀️	woman surfing: light skin tone	People & Body	person-sport
🏄🏼‍♀️	woman surfing: medium-light skin tone	People & Body	person-sport
🏄🏽‍♀️	woman surfing: medium skin tone	People & Body	person-sport
🏄🏾‍♀️	woman surfing: medium-dark skin tone	People & Body	person-sport
🏄🏿‍♀️	woman surfing: dark skin tone	People & Body	person-sport
🚣	person rowing boat	People & Body	person-sport
🚣🏻	person rowing boat: light skin tone	People & Body	person-sport
🚣🏼	person rowing boat: medium-light skin tone	People & Body	person-sport
🚣🏽	person rowing boat: medium skin tone	People & Body	person-sport
🚣🏾	person rowing boat: medium-dark skin tone	People & Body	person-sport
🚣🏿	person rowing boat: dark skin tone	People & Body	person-sport
🚣‍♂️	man rowing boat	People & Body	person-sport
🚣🏻‍♂️	man rowing boat: light skin tone	People & Body	person-sport
🚣🏼‍♂️	man rowing boat: medium-light skin tone	People & Body	person-sport
🚣🏽‍♂️	man rowing boat: medium skin tone	People & Body	person-sport
🚣🏾‍♂️	man rowing boat: medium-dark skin tone	People & Body	person-sport
🚣🏿‍♂️	man rowing boat: dark skin tone	People & Body	person-sport
🚣‍♀️	woman rowing boat	People & Body	person-sport
🚣🏻‍♀️	woman rowing boat: light skin tone	People & Body	person-sport
🚣🏼‍♀️	woman rowing boat: medium-light skin tone	People & Body	person-sport
🚣🏽‍♀️	woman rowing boat: medium skin tone	People & Body	person-sport
🚣🏾‍♀️	woman rowing boat: medium-dark skin tone	People & Body	person-sport
🚣🏿‍♀️	woman rowing boat: dark skin tone	People & Body	person-sport
🏊	person swimming	People & Body	person-sport
🏊🏻	person swimming: light skin tone	People & Body	person-sport
🏊🏼	person swimming: medium-light skin tone	People & Body	person-sport
🏊🏽	person swimming: medium skin tone	People & Body	person-sport
🏊🏾	person swimming: medium-dark skin tone	People & Body	person-sport
🏊🏿	person swimming: dark skin tone	People & Body	person-sport
🏊‍♂️	man swimming	People & Body	person-sport
🏊🏻‍♂️	man swimming: light skin tone	People & Body	person-sport
🏊🏼‍♂️	man swimming: medium-light skin tone	People & Body	person-sport
🏊🏽‍♂️	man swimming: medium skin tone	People & Body	person-sport
🏊🏾‍♂️	man swimming: medium-dark skin tone	People & Body	person-sport
🏊🏿‍♂️	man swimming: dark skin tone	People & Body	person-sport
🏊‍♀️	woman swimming	People & Body	person-sport
🏊🏻‍♀️	woman swimming: light skin tone	People & Body	person-sport
🏊🏼‍♀️	woman swimming: medium-light skin tone	People & Body	person-sport
🏊🏽‍♀️	woman swimming: medium skin tone	People & Body	person-sport
🏊🏾‍♀️	woman swimming: medium-dark skin tone	People & Body	person-sport
🏊🏿‍♀️	woman swimming: dark skin tone	People & Body	person-sport
⛹️	person bouncing ball	People & Body	person-sport
⛹🏻	person bouncing ball: light skin tone	People & Body	person-sport
⛹🏼	person bouncing ball: medium-light skin tone	People & Body	person-sport
⛹🏽	person bouncing ball: medium skin tone	People & Body	person-sport
⛹🏾	person bouncing ball: medium-dark skin tone	People & Body	person-sport
⛹🏿	person bouncing ball: dark skin tone	People & Body	person-sport
⛹️‍♂️	man bouncing ball	People & Body	person-sport
⛹🏻‍♂️	man bouncing ball: light skin tone	People & Body	person-sport
⛹🏼‍♂️	man bouncing ball: medium-light skin tone	People & Body	person-sport
⛹🏽‍♂️	man bouncing ball: medium skin tone	People & Body	person-sport
⛹🏾‍♂️	man bouncing ball: medium-dark skin tone	People & Body	person-sport
⛹🏿‍♂️	man bouncing ball: dark skin tone	People & Body	person-sport
⛹️‍♀️	woman bouncing ball	People & Body	person-sport
⛹🏻‍♀️	woman bouncing ball: light skin tone	People & Body	person-sport
⛹🏼‍♀️	woman bouncing ball: medium-light skin tone	People & Body	person-sport
⛹🏽‍♀️	woman bouncing ball: medium skin tone	People & Body	person-sport
⛹🏾‍♀️	woman bouncing ball: medium-dark skin tone	People & Body	person-sport
⛹🏿‍♀️	woman bouncing ball: dark skin tone	People & Body	person-sport
🏋️	person lifting weights	People & Body	person-sport
🏋🏻	person lifting weights: light skin tone	People & Body	person-sport
🏋🏼	person lifting weights: medium-light skin tone	People & Body	person-sport
🏋🏽	person lifting weights: medium skin tone	People & Body	person-sport
🏋🏾	person lifting weights: medium-dark skin tone	People & Body	person-sport
🏋🏿	person lifting weights: dark skin tone	People & Body	person-sport
🏋️‍♂️	man lifting weights	People & Body	person-sport
🏋🏻‍♂️	man lifting weights: light skin tone	People & Body	person-sport
🏋🏼‍♂️	man lifting weights: medium-light skin tone	People & Body	person-sport
🏋🏽‍♂️	man lifting weights: medium skin tone	People & Body	person-sport
🏋🏾‍♂️	man lifting weights: medium-dark skin tone	People & Body	person-sport
🏋🏿‍♂️	man lifting weights: dark skin tone	People & Body	person-sport
🏋️‍♀️	woman lifting weights	People & Body	person-sport
🏋🏻‍♀️	woman lifting weights: light skin tone	People & Body	person-sport
🏋🏼‍♀️	woman lifting weights: medium-light skin tone	People & Body	person-sport
🏋🏽‍♀️	woman lifting weights: medium skin tone	People & Body	person-sport
🏋🏾‍♀️	woman lifting weights: medium-dark skin tone	People & Body	person-sport
🏋🏿‍♀️	woman lifting weights: dark skin tone	People & Body	person-sport
🚴	person biking	People & Body	person-sport
🚴🏻	person biking: light skin tone	People & Body	person-sport
🚴🏼	person biking: medium-light skin tone	People & Body	person-sport
🚴🏽	person biking: medium skin tone	People & Body	person-sport
🚴🏾	person biking: medium-dark skin tone	People & Body	person-sport
🚴🏿	person biking: dark skin tone	People & Body	person-sport
🚴‍♂️	man biking	People & Body	person-sport
🚴🏻‍♂️	man biking: light skin tone	People & Body	person-sport
🚴🏼‍♂️	man biking: medium-light skin tone	People & Body	person-sport
🚴🏽‍♂️	man biking: medium skin tone	People & Body	person-sport
🚴🏾‍♂️	man biking: medium-dark skin tone	People & Body	person-sport
🚴🏿‍♂️	man biking: dark skin tone	People & Body	person-sport
🚴‍♀️	woman biking	People & Body	person-sport
🚴🏻‍♀️	woman biking: light skin tone	People & Body	person-sport
🚴🏼‍♀️	woman biking: medium-light skin tone	People & Body	person-sport
🚴🏽‍♀️	woman biking: medium skin tone	People & Body	person-sport
🚴🏾‍♀️	woman biking: medium-dark skin tone	People & Body	person-sport
🚴🏿‍♀️	woman biking: dark skin tone	People & Body	person-sport
🚵	person mountain biking	People & Body	person-sport
🚵🏻	person mountain biking: light skin tone	People & Body	person-sport
🚵🏼	person mountain biking: medium-light skin tone	People & Body	person-sport
🚵🏽	person mountain biking: medium skin tone	People & Body	person-sport
🚵🏾	person mountain biking: medium-dark skin tone	People & Body	person-sport
🚵🏿	person mountain biking: dark skin tone	People & Body	person-sport
🚵‍♂️	man mountain biking	People & Body	person-sport
🚵🏻‍♂️	man mountain biking: light skin tone	People & Body	person-sport
🚵🏼‍♂️	man mountain biking: medium-light skin tone	People & Body	person-sport
🚵🏽‍♂️	man mountain biking: medium skin tone	People & Body	person-sport
🚵🏾‍♂️	man mountain biking: medium-dark skin tone	People & Body	person-sport
🚵🏿‍♂️	man mountain biking: dark skin tone	People & Body	person-sport
🚵‍♀️	woman mountain biking	People & Body	person-sport
🚵🏻‍♀️	woman mountain biking: light skin tone	People & Body	person-sport
🚵🏼‍♀️	woman mountain biking: medium-light skin tone	People & Body	person-sport
🚵🏽‍♀️	woman mountain biking: medium skin tone	People & Body	person-sport
🚵🏾‍♀️	woman mountain biking: medium-dark skin tone	People & Body	person-sport
🚵🏿‍♀️	woman mountain biking: dark skin tone	People & Body	person-sport
🤸	person cartwheeling	People & Body	person-sport
🤸🏻	person cartwheeling: light skin tone	People & Body	person-sport
🤸🏼	person cartwheeling: medium-light skin tone	People & Body	person-sport
🤸🏽	person cartwheeling: medium skin tone	People & Body	person-sport
🤸🏾	person cartwheeling: medium-dark skin tone	People & Body	person-sport
🤸🏿	person cartwheeling: dark skin tone	People & Body	person-sport
🤸‍♂️	man cartwheeling	People & Body	person-sport
🤸🏻‍♂️	man cartwheeling: light skin tone	People & Body	person-sport
🤸🏼‍♂️	man cartwheeling: medium-light skin tone	People & Body	person-sport
🤸🏽‍♂️	man cartwheeling: medium skin tone	People & Body	person-sport
🤸🏾‍♂️	man cartwheeling: medium-dark skin tone	People & Body	person-sport
🤸🏿‍♂️	man cartwheeling: dark skin tone	People & Body	person-sport
🤸‍♀️	woman cartwheeling	People & Body	person-sport
🤸🏻‍♀️	woman cartwheeling: light skin tone	People & Body	person-sport
🤸🏼‍♀️	woman cartwheeling: medium-light skin tone	People & Body	person-sport
🤸🏽‍♀️	woman cartwheeling: medium skin tone	People & Body	person-sport
🤸🏾‍♀️	woman cartwheeling: medium-dark skin tone	People & Body	person-sport
🤸🏿‍♀️	woman cartwheeling: dark skin tone	People & Body	person-sport
🤼	people wrestling	People & Body	person-sport
🤼🏻	people wrestling: light skin tone	People & Body	person-sport
🤼🏼	people wrestling: medium-light skin tone	People & Body	person-sport
🤼🏽	people wrestling: medium skin tone	People & Body	person-sport
🤼🏾	people wrestling: medium-dark skin tone	People & Body	person-sport
🤼🏿	people wrestling: dark skin tone	People & Body	person-sport
🤼‍♂️	men wrestling	People & Body	person-sport
🤼🏻‍♂️	men wrestling: light skin tone	People & Body	person-sport
🤼🏼‍♂️	men wrestling: medium-light skin tone	People & Body	person-sport
🤼🏽‍♂️	men wrestling: medium skin tone	People & Body	person-sport
🤼🏾‍♂️	men wrestling: medium-dark skin tone	People & Body	person-sport
🤼🏿‍♂️	men wrestling: dark skin tone	People & Body	person-sport
🤼‍♀️	women wrestling	People & Body	person-sport
🤼🏻‍♀️	women wrestling: light skin tone	People & Body	person-sport
🤼🏼‍♀️	women wrestling: medium-light skin tone	People & Body	person-sport
🤼🏽‍♀️	women wrestling: medium skin tone	People & Body	person-sport
🤼🏾‍♀️	women wrestling: medium-dark skin tone	People & Body	person-sport
🤼🏿‍♀️	women wrestling: dark skin tone	People & Body	person-sport
🧑🏻‍🫯‍🧑🏼	people wrestling: light skin tone, medium-light skin tone	People & Body	person-sport
🧑🏻‍🫯‍🧑🏽	people wrestling: light skin tone, medium skin tone	People & Body	person-sport
🧑🏻‍🫯‍🧑🏾	people wrestling: light skin tone, medium-dark skin tone	People & Body	person-sport
🧑🏻‍🫯‍🧑🏿	people wrestling: light skin tone, dark skin tone	People & Body	person-sport
🧑🏼‍🫯‍🧑🏻	people wrestling: medium-light skin tone, light skin tone	People & Body	person-sport
🧑🏼‍🫯‍🧑🏽	people wrestling: medium-light skin tone, medium skin tone	People & Body	person-sport
🧑🏼‍🫯‍🧑🏾	people wrestling: medium-light skin tone, medium-dark skin tone	People & Body	person-sport
🧑🏼‍🫯‍🧑🏿	people wrestling: medium-light skin tone, dark skin tone	People & Body	person-sport
🧑🏽‍🫯‍🧑🏻	people wrestling: medium skin tone, light skin tone	People & Body	person-sport
🧑🏽‍🫯‍🧑🏼	people wrestling: medium skin tone, medium-light skin tone	People & Body	person-sport
🧑🏽‍🫯‍🧑🏾	people wrestling: medium skin tone, medium-dark skin tone	People & Body	person-sport
🧑🏽‍🫯‍🧑🏿	people wrestling: medium skin tone, dark skin tone	People & Body	person-sport
🧑🏾‍🫯‍🧑🏻	people wrestling: medium-dark skin tone, light skin tone	People & Body	person-sport
🧑🏾‍🫯‍🧑🏼	people wrestling: medium-dark skin tone, medium-light skin tone	People & Body	person-sport
🧑🏾‍🫯‍🧑🏽	people wrestling: medium-dark skin tone, medium skin tone	People & Body	person-sport
🧑🏾‍🫯‍🧑🏿	people wrestling: medium-dark skin tone, dark skin tone	People & Body	person-sport
🧑🏿‍🫯‍🧑🏻	people wrestling: dark skin tone, light skin tone	People & Body	person-sport
🧑🏿‍🫯‍🧑🏼	people wrestling: dark skin tone, medium-light skin tone	People & Body	person-sport
🧑🏿‍🫯‍🧑🏽	people wrestling: dark skin tone, medium skin tone	People & Body	person-sport
🧑🏿‍🫯‍🧑🏾	people wrestling: dark skin tone, medium-dark skin tone	People & Body	person-sport
👨🏻‍🫯‍👨🏼	men wrestling: light skin tone, medium-light skin tone	People & Body	person-sport
👨🏻‍🫯‍👨🏽	men wrestling: light skin tone, medium skin tone	People & Body	person-sport
"""

private const val EMOJI_DATA_3 = """
👨🏻‍🫯‍👨🏾	men wrestling: light skin tone, medium-dark skin tone	People & Body	person-sport
👨🏻‍🫯‍👨🏿	men wrestling: light skin tone, dark skin tone	People & Body	person-sport
👨🏼‍🫯‍👨🏻	men wrestling: medium-light skin tone, light skin tone	People & Body	person-sport
👨🏼‍🫯‍👨🏽	men wrestling: medium-light skin tone, medium skin tone	People & Body	person-sport
👨🏼‍🫯‍👨🏾	men wrestling: medium-light skin tone, medium-dark skin tone	People & Body	person-sport
👨🏼‍🫯‍👨🏿	men wrestling: medium-light skin tone, dark skin tone	People & Body	person-sport
👨🏽‍🫯‍👨🏻	men wrestling: medium skin tone, light skin tone	People & Body	person-sport
👨🏽‍🫯‍👨🏼	men wrestling: medium skin tone, medium-light skin tone	People & Body	person-sport
👨🏽‍🫯‍👨🏾	men wrestling: medium skin tone, medium-dark skin tone	People & Body	person-sport
👨🏽‍🫯‍👨🏿	men wrestling: medium skin tone, dark skin tone	People & Body	person-sport
👨🏾‍🫯‍👨🏻	men wrestling: medium-dark skin tone, light skin tone	People & Body	person-sport
👨🏾‍🫯‍👨🏼	men wrestling: medium-dark skin tone, medium-light skin tone	People & Body	person-sport
👨🏾‍🫯‍👨🏽	men wrestling: medium-dark skin tone, medium skin tone	People & Body	person-sport
👨🏾‍🫯‍👨🏿	men wrestling: medium-dark skin tone, dark skin tone	People & Body	person-sport
👨🏿‍🫯‍👨🏻	men wrestling: dark skin tone, light skin tone	People & Body	person-sport
👨🏿‍🫯‍👨🏼	men wrestling: dark skin tone, medium-light skin tone	People & Body	person-sport
👨🏿‍🫯‍👨🏽	men wrestling: dark skin tone, medium skin tone	People & Body	person-sport
👨🏿‍🫯‍👨🏾	men wrestling: dark skin tone, medium-dark skin tone	People & Body	person-sport
👩🏻‍🫯‍👩🏼	women wrestling: light skin tone, medium-light skin tone	People & Body	person-sport
👩🏻‍🫯‍👩🏽	women wrestling: light skin tone, medium skin tone	People & Body	person-sport
👩🏻‍🫯‍👩🏾	women wrestling: light skin tone, medium-dark skin tone	People & Body	person-sport
👩🏻‍🫯‍👩🏿	women wrestling: light skin tone, dark skin tone	People & Body	person-sport
👩🏼‍🫯‍👩🏻	women wrestling: medium-light skin tone, light skin tone	People & Body	person-sport
👩🏼‍🫯‍👩🏽	women wrestling: medium-light skin tone, medium skin tone	People & Body	person-sport
👩🏼‍🫯‍👩🏾	women wrestling: medium-light skin tone, medium-dark skin tone	People & Body	person-sport
👩🏼‍🫯‍👩🏿	women wrestling: medium-light skin tone, dark skin tone	People & Body	person-sport
👩🏽‍🫯‍👩🏻	women wrestling: medium skin tone, light skin tone	People & Body	person-sport
👩🏽‍🫯‍👩🏼	women wrestling: medium skin tone, medium-light skin tone	People & Body	person-sport
👩🏽‍🫯‍👩🏾	women wrestling: medium skin tone, medium-dark skin tone	People & Body	person-sport
👩🏽‍🫯‍👩🏿	women wrestling: medium skin tone, dark skin tone	People & Body	person-sport
👩🏾‍🫯‍👩🏻	women wrestling: medium-dark skin tone, light skin tone	People & Body	person-sport
👩🏾‍🫯‍👩🏼	women wrestling: medium-dark skin tone, medium-light skin tone	People & Body	person-sport
👩🏾‍🫯‍👩🏽	women wrestling: medium-dark skin tone, medium skin tone	People & Body	person-sport
👩🏾‍🫯‍👩🏿	women wrestling: medium-dark skin tone, dark skin tone	People & Body	person-sport
👩🏿‍🫯‍👩🏻	women wrestling: dark skin tone, light skin tone	People & Body	person-sport
👩🏿‍🫯‍👩🏼	women wrestling: dark skin tone, medium-light skin tone	People & Body	person-sport
👩🏿‍🫯‍👩🏽	women wrestling: dark skin tone, medium skin tone	People & Body	person-sport
👩🏿‍🫯‍👩🏾	women wrestling: dark skin tone, medium-dark skin tone	People & Body	person-sport
🤽	person playing water polo	People & Body	person-sport
🤽🏻	person playing water polo: light skin tone	People & Body	person-sport
🤽🏼	person playing water polo: medium-light skin tone	People & Body	person-sport
🤽🏽	person playing water polo: medium skin tone	People & Body	person-sport
🤽🏾	person playing water polo: medium-dark skin tone	People & Body	person-sport
🤽🏿	person playing water polo: dark skin tone	People & Body	person-sport
🤽‍♂️	man playing water polo	People & Body	person-sport
🤽🏻‍♂️	man playing water polo: light skin tone	People & Body	person-sport
🤽🏼‍♂️	man playing water polo: medium-light skin tone	People & Body	person-sport
🤽🏽‍♂️	man playing water polo: medium skin tone	People & Body	person-sport
🤽🏾‍♂️	man playing water polo: medium-dark skin tone	People & Body	person-sport
🤽🏿‍♂️	man playing water polo: dark skin tone	People & Body	person-sport
🤽‍♀️	woman playing water polo	People & Body	person-sport
🤽🏻‍♀️	woman playing water polo: light skin tone	People & Body	person-sport
🤽🏼‍♀️	woman playing water polo: medium-light skin tone	People & Body	person-sport
🤽🏽‍♀️	woman playing water polo: medium skin tone	People & Body	person-sport
🤽🏾‍♀️	woman playing water polo: medium-dark skin tone	People & Body	person-sport
🤽🏿‍♀️	woman playing water polo: dark skin tone	People & Body	person-sport
🤾	person playing handball	People & Body	person-sport
🤾🏻	person playing handball: light skin tone	People & Body	person-sport
🤾🏼	person playing handball: medium-light skin tone	People & Body	person-sport
🤾🏽	person playing handball: medium skin tone	People & Body	person-sport
🤾🏾	person playing handball: medium-dark skin tone	People & Body	person-sport
🤾🏿	person playing handball: dark skin tone	People & Body	person-sport
🤾‍♂️	man playing handball	People & Body	person-sport
🤾🏻‍♂️	man playing handball: light skin tone	People & Body	person-sport
🤾🏼‍♂️	man playing handball: medium-light skin tone	People & Body	person-sport
🤾🏽‍♂️	man playing handball: medium skin tone	People & Body	person-sport
🤾🏾‍♂️	man playing handball: medium-dark skin tone	People & Body	person-sport
🤾🏿‍♂️	man playing handball: dark skin tone	People & Body	person-sport
🤾‍♀️	woman playing handball	People & Body	person-sport
🤾🏻‍♀️	woman playing handball: light skin tone	People & Body	person-sport
🤾🏼‍♀️	woman playing handball: medium-light skin tone	People & Body	person-sport
🤾🏽‍♀️	woman playing handball: medium skin tone	People & Body	person-sport
🤾🏾‍♀️	woman playing handball: medium-dark skin tone	People & Body	person-sport
🤾🏿‍♀️	woman playing handball: dark skin tone	People & Body	person-sport
🤹	person juggling	People & Body	person-sport
🤹🏻	person juggling: light skin tone	People & Body	person-sport
🤹🏼	person juggling: medium-light skin tone	People & Body	person-sport
🤹🏽	person juggling: medium skin tone	People & Body	person-sport
🤹🏾	person juggling: medium-dark skin tone	People & Body	person-sport
🤹🏿	person juggling: dark skin tone	People & Body	person-sport
🤹‍♂️	man juggling	People & Body	person-sport
🤹🏻‍♂️	man juggling: light skin tone	People & Body	person-sport
🤹🏼‍♂️	man juggling: medium-light skin tone	People & Body	person-sport
🤹🏽‍♂️	man juggling: medium skin tone	People & Body	person-sport
🤹🏾‍♂️	man juggling: medium-dark skin tone	People & Body	person-sport
🤹🏿‍♂️	man juggling: dark skin tone	People & Body	person-sport
🤹‍♀️	woman juggling	People & Body	person-sport
🤹🏻‍♀️	woman juggling: light skin tone	People & Body	person-sport
🤹🏼‍♀️	woman juggling: medium-light skin tone	People & Body	person-sport
🤹🏽‍♀️	woman juggling: medium skin tone	People & Body	person-sport
🤹🏾‍♀️	woman juggling: medium-dark skin tone	People & Body	person-sport
🤹🏿‍♀️	woman juggling: dark skin tone	People & Body	person-sport
🧘	person in lotus position	People & Body	person-resting
🧘🏻	person in lotus position: light skin tone	People & Body	person-resting
🧘🏼	person in lotus position: medium-light skin tone	People & Body	person-resting
🧘🏽	person in lotus position: medium skin tone	People & Body	person-resting
🧘🏾	person in lotus position: medium-dark skin tone	People & Body	person-resting
🧘🏿	person in lotus position: dark skin tone	People & Body	person-resting
🧘‍♂️	man in lotus position	People & Body	person-resting
🧘🏻‍♂️	man in lotus position: light skin tone	People & Body	person-resting
🧘🏼‍♂️	man in lotus position: medium-light skin tone	People & Body	person-resting
🧘🏽‍♂️	man in lotus position: medium skin tone	People & Body	person-resting
🧘🏾‍♂️	man in lotus position: medium-dark skin tone	People & Body	person-resting
🧘🏿‍♂️	man in lotus position: dark skin tone	People & Body	person-resting
🧘‍♀️	woman in lotus position	People & Body	person-resting
🧘🏻‍♀️	woman in lotus position: light skin tone	People & Body	person-resting
🧘🏼‍♀️	woman in lotus position: medium-light skin tone	People & Body	person-resting
🧘🏽‍♀️	woman in lotus position: medium skin tone	People & Body	person-resting
🧘🏾‍♀️	woman in lotus position: medium-dark skin tone	People & Body	person-resting
🧘🏿‍♀️	woman in lotus position: dark skin tone	People & Body	person-resting
🛀	person taking bath	People & Body	person-resting
🛀🏻	person taking bath: light skin tone	People & Body	person-resting
🛀🏼	person taking bath: medium-light skin tone	People & Body	person-resting
🛀🏽	person taking bath: medium skin tone	People & Body	person-resting
🛀🏾	person taking bath: medium-dark skin tone	People & Body	person-resting
🛀🏿	person taking bath: dark skin tone	People & Body	person-resting
🛌	person in bed	People & Body	person-resting
🛌🏻	person in bed: light skin tone	People & Body	person-resting
🛌🏼	person in bed: medium-light skin tone	People & Body	person-resting
🛌🏽	person in bed: medium skin tone	People & Body	person-resting
🛌🏾	person in bed: medium-dark skin tone	People & Body	person-resting
🛌🏿	person in bed: dark skin tone	People & Body	person-resting
🧑‍🤝‍🧑	people holding hands	People & Body	family
🧑🏻‍🤝‍🧑🏻	people holding hands: light skin tone	People & Body	family
🧑🏻‍🤝‍🧑🏼	people holding hands: light skin tone, medium-light skin tone	People & Body	family
🧑🏻‍🤝‍🧑🏽	people holding hands: light skin tone, medium skin tone	People & Body	family
🧑🏻‍🤝‍🧑🏾	people holding hands: light skin tone, medium-dark skin tone	People & Body	family
🧑🏻‍🤝‍🧑🏿	people holding hands: light skin tone, dark skin tone	People & Body	family
🧑🏼‍🤝‍🧑🏻	people holding hands: medium-light skin tone, light skin tone	People & Body	family
🧑🏼‍🤝‍🧑🏼	people holding hands: medium-light skin tone	People & Body	family
🧑🏼‍🤝‍🧑🏽	people holding hands: medium-light skin tone, medium skin tone	People & Body	family
🧑🏼‍🤝‍🧑🏾	people holding hands: medium-light skin tone, medium-dark skin tone	People & Body	family
🧑🏼‍🤝‍🧑🏿	people holding hands: medium-light skin tone, dark skin tone	People & Body	family
🧑🏽‍🤝‍🧑🏻	people holding hands: medium skin tone, light skin tone	People & Body	family
🧑🏽‍🤝‍🧑🏼	people holding hands: medium skin tone, medium-light skin tone	People & Body	family
🧑🏽‍🤝‍🧑🏽	people holding hands: medium skin tone	People & Body	family
🧑🏽‍🤝‍🧑🏾	people holding hands: medium skin tone, medium-dark skin tone	People & Body	family
🧑🏽‍🤝‍🧑🏿	people holding hands: medium skin tone, dark skin tone	People & Body	family
🧑🏾‍🤝‍🧑🏻	people holding hands: medium-dark skin tone, light skin tone	People & Body	family
🧑🏾‍🤝‍🧑🏼	people holding hands: medium-dark skin tone, medium-light skin tone	People & Body	family
🧑🏾‍🤝‍🧑🏽	people holding hands: medium-dark skin tone, medium skin tone	People & Body	family
🧑🏾‍🤝‍🧑🏾	people holding hands: medium-dark skin tone	People & Body	family
🧑🏾‍🤝‍🧑🏿	people holding hands: medium-dark skin tone, dark skin tone	People & Body	family
🧑🏿‍🤝‍🧑🏻	people holding hands: dark skin tone, light skin tone	People & Body	family
🧑🏿‍🤝‍🧑🏼	people holding hands: dark skin tone, medium-light skin tone	People & Body	family
🧑🏿‍🤝‍🧑🏽	people holding hands: dark skin tone, medium skin tone	People & Body	family
🧑🏿‍🤝‍🧑🏾	people holding hands: dark skin tone, medium-dark skin tone	People & Body	family
🧑🏿‍🤝‍🧑🏿	people holding hands: dark skin tone	People & Body	family
👭	women holding hands	People & Body	family
👭🏻	women holding hands: light skin tone	People & Body	family
👩🏻‍🤝‍👩🏼	women holding hands: light skin tone, medium-light skin tone	People & Body	family
👩🏻‍🤝‍👩🏽	women holding hands: light skin tone, medium skin tone	People & Body	family
👩🏻‍🤝‍👩🏾	women holding hands: light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍🤝‍👩🏿	women holding hands: light skin tone, dark skin tone	People & Body	family
👩🏼‍🤝‍👩🏻	women holding hands: medium-light skin tone, light skin tone	People & Body	family
👭🏼	women holding hands: medium-light skin tone	People & Body	family
👩🏼‍🤝‍👩🏽	women holding hands: medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍🤝‍👩🏾	women holding hands: medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍🤝‍👩🏿	women holding hands: medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍🤝‍👩🏻	women holding hands: medium skin tone, light skin tone	People & Body	family
👩🏽‍🤝‍👩🏼	women holding hands: medium skin tone, medium-light skin tone	People & Body	family
👭🏽	women holding hands: medium skin tone	People & Body	family
👩🏽‍🤝‍👩🏾	women holding hands: medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍🤝‍👩🏿	women holding hands: medium skin tone, dark skin tone	People & Body	family
👩🏾‍🤝‍👩🏻	women holding hands: medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍🤝‍👩🏼	women holding hands: medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍🤝‍👩🏽	women holding hands: medium-dark skin tone, medium skin tone	People & Body	family
👭🏾	women holding hands: medium-dark skin tone	People & Body	family
👩🏾‍🤝‍👩🏿	women holding hands: medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍🤝‍👩🏻	women holding hands: dark skin tone, light skin tone	People & Body	family
👩🏿‍🤝‍👩🏼	women holding hands: dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍🤝‍👩🏽	women holding hands: dark skin tone, medium skin tone	People & Body	family
👩🏿‍🤝‍👩🏾	women holding hands: dark skin tone, medium-dark skin tone	People & Body	family
👭🏿	women holding hands: dark skin tone	People & Body	family
👫	woman and man holding hands	People & Body	family
👫🏻	woman and man holding hands: light skin tone	People & Body	family
👩🏻‍🤝‍👨🏼	woman and man holding hands: light skin tone, medium-light skin tone	People & Body	family
👩🏻‍🤝‍👨🏽	woman and man holding hands: light skin tone, medium skin tone	People & Body	family
👩🏻‍🤝‍👨🏾	woman and man holding hands: light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍🤝‍👨🏿	woman and man holding hands: light skin tone, dark skin tone	People & Body	family
👩🏼‍🤝‍👨🏻	woman and man holding hands: medium-light skin tone, light skin tone	People & Body	family
👫🏼	woman and man holding hands: medium-light skin tone	People & Body	family
👩🏼‍🤝‍👨🏽	woman and man holding hands: medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍🤝‍👨🏾	woman and man holding hands: medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍🤝‍👨🏿	woman and man holding hands: medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍🤝‍👨🏻	woman and man holding hands: medium skin tone, light skin tone	People & Body	family
👩🏽‍🤝‍👨🏼	woman and man holding hands: medium skin tone, medium-light skin tone	People & Body	family
👫🏽	woman and man holding hands: medium skin tone	People & Body	family
👩🏽‍🤝‍👨🏾	woman and man holding hands: medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍🤝‍👨🏿	woman and man holding hands: medium skin tone, dark skin tone	People & Body	family
👩🏾‍🤝‍👨🏻	woman and man holding hands: medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍🤝‍👨🏼	woman and man holding hands: medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍🤝‍👨🏽	woman and man holding hands: medium-dark skin tone, medium skin tone	People & Body	family
👫🏾	woman and man holding hands: medium-dark skin tone	People & Body	family
👩🏾‍🤝‍👨🏿	woman and man holding hands: medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍🤝‍👨🏻	woman and man holding hands: dark skin tone, light skin tone	People & Body	family
👩🏿‍🤝‍👨🏼	woman and man holding hands: dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍🤝‍👨🏽	woman and man holding hands: dark skin tone, medium skin tone	People & Body	family
👩🏿‍🤝‍👨🏾	woman and man holding hands: dark skin tone, medium-dark skin tone	People & Body	family
👫🏿	woman and man holding hands: dark skin tone	People & Body	family
👬	men holding hands	People & Body	family
👬🏻	men holding hands: light skin tone	People & Body	family
👨🏻‍🤝‍👨🏼	men holding hands: light skin tone, medium-light skin tone	People & Body	family
👨🏻‍🤝‍👨🏽	men holding hands: light skin tone, medium skin tone	People & Body	family
👨🏻‍🤝‍👨🏾	men holding hands: light skin tone, medium-dark skin tone	People & Body	family
👨🏻‍🤝‍👨🏿	men holding hands: light skin tone, dark skin tone	People & Body	family
👨🏼‍🤝‍👨🏻	men holding hands: medium-light skin tone, light skin tone	People & Body	family
👬🏼	men holding hands: medium-light skin tone	People & Body	family
👨🏼‍🤝‍👨🏽	men holding hands: medium-light skin tone, medium skin tone	People & Body	family
👨🏼‍🤝‍👨🏾	men holding hands: medium-light skin tone, medium-dark skin tone	People & Body	family
👨🏼‍🤝‍👨🏿	men holding hands: medium-light skin tone, dark skin tone	People & Body	family
👨🏽‍🤝‍👨🏻	men holding hands: medium skin tone, light skin tone	People & Body	family
👨🏽‍🤝‍👨🏼	men holding hands: medium skin tone, medium-light skin tone	People & Body	family
👬🏽	men holding hands: medium skin tone	People & Body	family
👨🏽‍🤝‍👨🏾	men holding hands: medium skin tone, medium-dark skin tone	People & Body	family
👨🏽‍🤝‍👨🏿	men holding hands: medium skin tone, dark skin tone	People & Body	family
👨🏾‍🤝‍👨🏻	men holding hands: medium-dark skin tone, light skin tone	People & Body	family
👨🏾‍🤝‍👨🏼	men holding hands: medium-dark skin tone, medium-light skin tone	People & Body	family
👨🏾‍🤝‍👨🏽	men holding hands: medium-dark skin tone, medium skin tone	People & Body	family
👬🏾	men holding hands: medium-dark skin tone	People & Body	family
👨🏾‍🤝‍👨🏿	men holding hands: medium-dark skin tone, dark skin tone	People & Body	family
👨🏿‍🤝‍👨🏻	men holding hands: dark skin tone, light skin tone	People & Body	family
👨🏿‍🤝‍👨🏼	men holding hands: dark skin tone, medium-light skin tone	People & Body	family
👨🏿‍🤝‍👨🏽	men holding hands: dark skin tone, medium skin tone	People & Body	family
👨🏿‍🤝‍👨🏾	men holding hands: dark skin tone, medium-dark skin tone	People & Body	family
👬🏿	men holding hands: dark skin tone	People & Body	family
💏	kiss	People & Body	family
💏🏻	kiss: light skin tone	People & Body	family
💏🏼	kiss: medium-light skin tone	People & Body	family
💏🏽	kiss: medium skin tone	People & Body	family
💏🏾	kiss: medium-dark skin tone	People & Body	family
💏🏿	kiss: dark skin tone	People & Body	family
🧑🏻‍❤️‍💋‍🧑🏼	kiss: person, person, light skin tone, medium-light skin tone	People & Body	family
🧑🏻‍❤️‍💋‍🧑🏽	kiss: person, person, light skin tone, medium skin tone	People & Body	family
🧑🏻‍❤️‍💋‍🧑🏾	kiss: person, person, light skin tone, medium-dark skin tone	People & Body	family
🧑🏻‍❤️‍💋‍🧑🏿	kiss: person, person, light skin tone, dark skin tone	People & Body	family
🧑🏼‍❤️‍💋‍🧑🏻	kiss: person, person, medium-light skin tone, light skin tone	People & Body	family
🧑🏼‍❤️‍💋‍🧑🏽	kiss: person, person, medium-light skin tone, medium skin tone	People & Body	family
🧑🏼‍❤️‍💋‍🧑🏾	kiss: person, person, medium-light skin tone, medium-dark skin tone	People & Body	family
🧑🏼‍❤️‍💋‍🧑🏿	kiss: person, person, medium-light skin tone, dark skin tone	People & Body	family
🧑🏽‍❤️‍💋‍🧑🏻	kiss: person, person, medium skin tone, light skin tone	People & Body	family
🧑🏽‍❤️‍💋‍🧑🏼	kiss: person, person, medium skin tone, medium-light skin tone	People & Body	family
🧑🏽‍❤️‍💋‍🧑🏾	kiss: person, person, medium skin tone, medium-dark skin tone	People & Body	family
🧑🏽‍❤️‍💋‍🧑🏿	kiss: person, person, medium skin tone, dark skin tone	People & Body	family
🧑🏾‍❤️‍💋‍🧑🏻	kiss: person, person, medium-dark skin tone, light skin tone	People & Body	family
🧑🏾‍❤️‍💋‍🧑🏼	kiss: person, person, medium-dark skin tone, medium-light skin tone	People & Body	family
🧑🏾‍❤️‍💋‍🧑🏽	kiss: person, person, medium-dark skin tone, medium skin tone	People & Body	family
🧑🏾‍❤️‍💋‍🧑🏿	kiss: person, person, medium-dark skin tone, dark skin tone	People & Body	family
🧑🏿‍❤️‍💋‍🧑🏻	kiss: person, person, dark skin tone, light skin tone	People & Body	family
🧑🏿‍❤️‍💋‍🧑🏼	kiss: person, person, dark skin tone, medium-light skin tone	People & Body	family
🧑🏿‍❤️‍💋‍🧑🏽	kiss: person, person, dark skin tone, medium skin tone	People & Body	family
🧑🏿‍❤️‍💋‍🧑🏾	kiss: person, person, dark skin tone, medium-dark skin tone	People & Body	family
👩‍❤️‍💋‍👨	kiss: woman, man	People & Body	family
👩🏻‍❤️‍💋‍👨🏻	kiss: woman, man, light skin tone	People & Body	family
👩🏻‍❤️‍💋‍👨🏼	kiss: woman, man, light skin tone, medium-light skin tone	People & Body	family
👩🏻‍❤️‍💋‍👨🏽	kiss: woman, man, light skin tone, medium skin tone	People & Body	family
👩🏻‍❤️‍💋‍👨🏾	kiss: woman, man, light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍❤️‍💋‍👨🏿	kiss: woman, man, light skin tone, dark skin tone	People & Body	family
👩🏼‍❤️‍💋‍👨🏻	kiss: woman, man, medium-light skin tone, light skin tone	People & Body	family
👩🏼‍❤️‍💋‍👨🏼	kiss: woman, man, medium-light skin tone	People & Body	family
👩🏼‍❤️‍💋‍👨🏽	kiss: woman, man, medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍❤️‍💋‍👨🏾	kiss: woman, man, medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍❤️‍💋‍👨🏿	kiss: woman, man, medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍❤️‍💋‍👨🏻	kiss: woman, man, medium skin tone, light skin tone	People & Body	family
👩🏽‍❤️‍💋‍👨🏼	kiss: woman, man, medium skin tone, medium-light skin tone	People & Body	family
👩🏽‍❤️‍💋‍👨🏽	kiss: woman, man, medium skin tone	People & Body	family
👩🏽‍❤️‍💋‍👨🏾	kiss: woman, man, medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍❤️‍💋‍👨🏿	kiss: woman, man, medium skin tone, dark skin tone	People & Body	family
👩🏾‍❤️‍💋‍👨🏻	kiss: woman, man, medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍❤️‍💋‍👨🏼	kiss: woman, man, medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍❤️‍💋‍👨🏽	kiss: woman, man, medium-dark skin tone, medium skin tone	People & Body	family
👩🏾‍❤️‍💋‍👨🏾	kiss: woman, man, medium-dark skin tone	People & Body	family
👩🏾‍❤️‍💋‍👨🏿	kiss: woman, man, medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍❤️‍💋‍👨🏻	kiss: woman, man, dark skin tone, light skin tone	People & Body	family
👩🏿‍❤️‍💋‍👨🏼	kiss: woman, man, dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍❤️‍💋‍👨🏽	kiss: woman, man, dark skin tone, medium skin tone	People & Body	family
👩🏿‍❤️‍💋‍👨🏾	kiss: woman, man, dark skin tone, medium-dark skin tone	People & Body	family
👩🏿‍❤️‍💋‍👨🏿	kiss: woman, man, dark skin tone	People & Body	family
👨‍❤️‍💋‍👨	kiss: man, man	People & Body	family
👨🏻‍❤️‍💋‍👨🏻	kiss: man, man, light skin tone	People & Body	family
👨🏻‍❤️‍💋‍👨🏼	kiss: man, man, light skin tone, medium-light skin tone	People & Body	family
👨🏻‍❤️‍💋‍👨🏽	kiss: man, man, light skin tone, medium skin tone	People & Body	family
👨🏻‍❤️‍💋‍👨🏾	kiss: man, man, light skin tone, medium-dark skin tone	People & Body	family
👨🏻‍❤️‍💋‍👨🏿	kiss: man, man, light skin tone, dark skin tone	People & Body	family
👨🏼‍❤️‍💋‍👨🏻	kiss: man, man, medium-light skin tone, light skin tone	People & Body	family
👨🏼‍❤️‍💋‍👨🏼	kiss: man, man, medium-light skin tone	People & Body	family
👨🏼‍❤️‍💋‍👨🏽	kiss: man, man, medium-light skin tone, medium skin tone	People & Body	family
👨🏼‍❤️‍💋‍👨🏾	kiss: man, man, medium-light skin tone, medium-dark skin tone	People & Body	family
👨🏼‍❤️‍💋‍👨🏿	kiss: man, man, medium-light skin tone, dark skin tone	People & Body	family
👨🏽‍❤️‍💋‍👨🏻	kiss: man, man, medium skin tone, light skin tone	People & Body	family
👨🏽‍❤️‍💋‍👨🏼	kiss: man, man, medium skin tone, medium-light skin tone	People & Body	family
👨🏽‍❤️‍💋‍👨🏽	kiss: man, man, medium skin tone	People & Body	family
👨🏽‍❤️‍💋‍👨🏾	kiss: man, man, medium skin tone, medium-dark skin tone	People & Body	family
👨🏽‍❤️‍💋‍👨🏿	kiss: man, man, medium skin tone, dark skin tone	People & Body	family
👨🏾‍❤️‍💋‍👨🏻	kiss: man, man, medium-dark skin tone, light skin tone	People & Body	family
👨🏾‍❤️‍💋‍👨🏼	kiss: man, man, medium-dark skin tone, medium-light skin tone	People & Body	family
👨🏾‍❤️‍💋‍👨🏽	kiss: man, man, medium-dark skin tone, medium skin tone	People & Body	family
👨🏾‍❤️‍💋‍👨🏾	kiss: man, man, medium-dark skin tone	People & Body	family
👨🏾‍❤️‍💋‍👨🏿	kiss: man, man, medium-dark skin tone, dark skin tone	People & Body	family
👨🏿‍❤️‍💋‍👨🏻	kiss: man, man, dark skin tone, light skin tone	People & Body	family
👨🏿‍❤️‍💋‍👨🏼	kiss: man, man, dark skin tone, medium-light skin tone	People & Body	family
👨🏿‍❤️‍💋‍👨🏽	kiss: man, man, dark skin tone, medium skin tone	People & Body	family
👨🏿‍❤️‍💋‍👨🏾	kiss: man, man, dark skin tone, medium-dark skin tone	People & Body	family
👨🏿‍❤️‍💋‍👨🏿	kiss: man, man, dark skin tone	People & Body	family
👩‍❤️‍💋‍👩	kiss: woman, woman	People & Body	family
👩🏻‍❤️‍💋‍👩🏻	kiss: woman, woman, light skin tone	People & Body	family
👩🏻‍❤️‍💋‍👩🏼	kiss: woman, woman, light skin tone, medium-light skin tone	People & Body	family
👩🏻‍❤️‍💋‍👩🏽	kiss: woman, woman, light skin tone, medium skin tone	People & Body	family
👩🏻‍❤️‍💋‍👩🏾	kiss: woman, woman, light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍❤️‍💋‍👩🏿	kiss: woman, woman, light skin tone, dark skin tone	People & Body	family
👩🏼‍❤️‍💋‍👩🏻	kiss: woman, woman, medium-light skin tone, light skin tone	People & Body	family
👩🏼‍❤️‍💋‍👩🏼	kiss: woman, woman, medium-light skin tone	People & Body	family
👩🏼‍❤️‍💋‍👩🏽	kiss: woman, woman, medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍❤️‍💋‍👩🏾	kiss: woman, woman, medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍❤️‍💋‍👩🏿	kiss: woman, woman, medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍❤️‍💋‍👩🏻	kiss: woman, woman, medium skin tone, light skin tone	People & Body	family
👩🏽‍❤️‍💋‍👩🏼	kiss: woman, woman, medium skin tone, medium-light skin tone	People & Body	family
👩🏽‍❤️‍💋‍👩🏽	kiss: woman, woman, medium skin tone	People & Body	family
👩🏽‍❤️‍💋‍👩🏾	kiss: woman, woman, medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍❤️‍💋‍👩🏿	kiss: woman, woman, medium skin tone, dark skin tone	People & Body	family
👩🏾‍❤️‍💋‍👩🏻	kiss: woman, woman, medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍❤️‍💋‍👩🏼	kiss: woman, woman, medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍❤️‍💋‍👩🏽	kiss: woman, woman, medium-dark skin tone, medium skin tone	People & Body	family
👩🏾‍❤️‍💋‍👩🏾	kiss: woman, woman, medium-dark skin tone	People & Body	family
👩🏾‍❤️‍💋‍👩🏿	kiss: woman, woman, medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍❤️‍💋‍👩🏻	kiss: woman, woman, dark skin tone, light skin tone	People & Body	family
👩🏿‍❤️‍💋‍👩🏼	kiss: woman, woman, dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍❤️‍💋‍👩🏽	kiss: woman, woman, dark skin tone, medium skin tone	People & Body	family
👩🏿‍❤️‍💋‍👩🏾	kiss: woman, woman, dark skin tone, medium-dark skin tone	People & Body	family
👩🏿‍❤️‍💋‍👩🏿	kiss: woman, woman, dark skin tone	People & Body	family
💑	couple with heart	People & Body	family
💑🏻	couple with heart: light skin tone	People & Body	family
💑🏼	couple with heart: medium-light skin tone	People & Body	family
💑🏽	couple with heart: medium skin tone	People & Body	family
💑🏾	couple with heart: medium-dark skin tone	People & Body	family
💑🏿	couple with heart: dark skin tone	People & Body	family
🧑🏻‍❤️‍🧑🏼	couple with heart: person, person, light skin tone, medium-light skin tone	People & Body	family
🧑🏻‍❤️‍🧑🏽	couple with heart: person, person, light skin tone, medium skin tone	People & Body	family
🧑🏻‍❤️‍🧑🏾	couple with heart: person, person, light skin tone, medium-dark skin tone	People & Body	family
🧑🏻‍❤️‍🧑🏿	couple with heart: person, person, light skin tone, dark skin tone	People & Body	family
🧑🏼‍❤️‍🧑🏻	couple with heart: person, person, medium-light skin tone, light skin tone	People & Body	family
🧑🏼‍❤️‍🧑🏽	couple with heart: person, person, medium-light skin tone, medium skin tone	People & Body	family
🧑🏼‍❤️‍🧑🏾	couple with heart: person, person, medium-light skin tone, medium-dark skin tone	People & Body	family
🧑🏼‍❤️‍🧑🏿	couple with heart: person, person, medium-light skin tone, dark skin tone	People & Body	family
🧑🏽‍❤️‍🧑🏻	couple with heart: person, person, medium skin tone, light skin tone	People & Body	family
🧑🏽‍❤️‍🧑🏼	couple with heart: person, person, medium skin tone, medium-light skin tone	People & Body	family
🧑🏽‍❤️‍🧑🏾	couple with heart: person, person, medium skin tone, medium-dark skin tone	People & Body	family
🧑🏽‍❤️‍🧑🏿	couple with heart: person, person, medium skin tone, dark skin tone	People & Body	family
🧑🏾‍❤️‍🧑🏻	couple with heart: person, person, medium-dark skin tone, light skin tone	People & Body	family
🧑🏾‍❤️‍🧑🏼	couple with heart: person, person, medium-dark skin tone, medium-light skin tone	People & Body	family
🧑🏾‍❤️‍🧑🏽	couple with heart: person, person, medium-dark skin tone, medium skin tone	People & Body	family
🧑🏾‍❤️‍🧑🏿	couple with heart: person, person, medium-dark skin tone, dark skin tone	People & Body	family
🧑🏿‍❤️‍🧑🏻	couple with heart: person, person, dark skin tone, light skin tone	People & Body	family
🧑🏿‍❤️‍🧑🏼	couple with heart: person, person, dark skin tone, medium-light skin tone	People & Body	family
🧑🏿‍❤️‍🧑🏽	couple with heart: person, person, dark skin tone, medium skin tone	People & Body	family
🧑🏿‍❤️‍🧑🏾	couple with heart: person, person, dark skin tone, medium-dark skin tone	People & Body	family
👩‍❤️‍👨	couple with heart: woman, man	People & Body	family
👩🏻‍❤️‍👨🏻	couple with heart: woman, man, light skin tone	People & Body	family
👩🏻‍❤️‍👨🏼	couple with heart: woman, man, light skin tone, medium-light skin tone	People & Body	family
👩🏻‍❤️‍👨🏽	couple with heart: woman, man, light skin tone, medium skin tone	People & Body	family
👩🏻‍❤️‍👨🏾	couple with heart: woman, man, light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍❤️‍👨🏿	couple with heart: woman, man, light skin tone, dark skin tone	People & Body	family
👩🏼‍❤️‍👨🏻	couple with heart: woman, man, medium-light skin tone, light skin tone	People & Body	family
👩🏼‍❤️‍👨🏼	couple with heart: woman, man, medium-light skin tone	People & Body	family
👩🏼‍❤️‍👨🏽	couple with heart: woman, man, medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍❤️‍👨🏾	couple with heart: woman, man, medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍❤️‍👨🏿	couple with heart: woman, man, medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍❤️‍👨🏻	couple with heart: woman, man, medium skin tone, light skin tone	People & Body	family
👩🏽‍❤️‍👨🏼	couple with heart: woman, man, medium skin tone, medium-light skin tone	People & Body	family
👩🏽‍❤️‍👨🏽	couple with heart: woman, man, medium skin tone	People & Body	family
👩🏽‍❤️‍👨🏾	couple with heart: woman, man, medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍❤️‍👨🏿	couple with heart: woman, man, medium skin tone, dark skin tone	People & Body	family
👩🏾‍❤️‍👨🏻	couple with heart: woman, man, medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍❤️‍👨🏼	couple with heart: woman, man, medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍❤️‍👨🏽	couple with heart: woman, man, medium-dark skin tone, medium skin tone	People & Body	family
👩🏾‍❤️‍👨🏾	couple with heart: woman, man, medium-dark skin tone	People & Body	family
👩🏾‍❤️‍👨🏿	couple with heart: woman, man, medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍❤️‍👨🏻	couple with heart: woman, man, dark skin tone, light skin tone	People & Body	family
👩🏿‍❤️‍👨🏼	couple with heart: woman, man, dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍❤️‍👨🏽	couple with heart: woman, man, dark skin tone, medium skin tone	People & Body	family
👩🏿‍❤️‍👨🏾	couple with heart: woman, man, dark skin tone, medium-dark skin tone	People & Body	family
👩🏿‍❤️‍👨🏿	couple with heart: woman, man, dark skin tone	People & Body	family
👨‍❤️‍👨	couple with heart: man, man	People & Body	family
👨🏻‍❤️‍👨🏻	couple with heart: man, man, light skin tone	People & Body	family
👨🏻‍❤️‍👨🏼	couple with heart: man, man, light skin tone, medium-light skin tone	People & Body	family
👨🏻‍❤️‍👨🏽	couple with heart: man, man, light skin tone, medium skin tone	People & Body	family
👨🏻‍❤️‍👨🏾	couple with heart: man, man, light skin tone, medium-dark skin tone	People & Body	family
👨🏻‍❤️‍👨🏿	couple with heart: man, man, light skin tone, dark skin tone	People & Body	family
👨🏼‍❤️‍👨🏻	couple with heart: man, man, medium-light skin tone, light skin tone	People & Body	family
👨🏼‍❤️‍👨🏼	couple with heart: man, man, medium-light skin tone	People & Body	family
👨🏼‍❤️‍👨🏽	couple with heart: man, man, medium-light skin tone, medium skin tone	People & Body	family
👨🏼‍❤️‍👨🏾	couple with heart: man, man, medium-light skin tone, medium-dark skin tone	People & Body	family
👨🏼‍❤️‍👨🏿	couple with heart: man, man, medium-light skin tone, dark skin tone	People & Body	family
👨🏽‍❤️‍👨🏻	couple with heart: man, man, medium skin tone, light skin tone	People & Body	family
👨🏽‍❤️‍👨🏼	couple with heart: man, man, medium skin tone, medium-light skin tone	People & Body	family
👨🏽‍❤️‍👨🏽	couple with heart: man, man, medium skin tone	People & Body	family
👨🏽‍❤️‍👨🏾	couple with heart: man, man, medium skin tone, medium-dark skin tone	People & Body	family
👨🏽‍❤️‍👨🏿	couple with heart: man, man, medium skin tone, dark skin tone	People & Body	family
👨🏾‍❤️‍👨🏻	couple with heart: man, man, medium-dark skin tone, light skin tone	People & Body	family
👨🏾‍❤️‍👨🏼	couple with heart: man, man, medium-dark skin tone, medium-light skin tone	People & Body	family
👨🏾‍❤️‍👨🏽	couple with heart: man, man, medium-dark skin tone, medium skin tone	People & Body	family
👨🏾‍❤️‍👨🏾	couple with heart: man, man, medium-dark skin tone	People & Body	family
👨🏾‍❤️‍👨🏿	couple with heart: man, man, medium-dark skin tone, dark skin tone	People & Body	family
👨🏿‍❤️‍👨🏻	couple with heart: man, man, dark skin tone, light skin tone	People & Body	family
👨🏿‍❤️‍👨🏼	couple with heart: man, man, dark skin tone, medium-light skin tone	People & Body	family
👨🏿‍❤️‍👨🏽	couple with heart: man, man, dark skin tone, medium skin tone	People & Body	family
👨🏿‍❤️‍👨🏾	couple with heart: man, man, dark skin tone, medium-dark skin tone	People & Body	family
👨🏿‍❤️‍👨🏿	couple with heart: man, man, dark skin tone	People & Body	family
👩‍❤️‍👩	couple with heart: woman, woman	People & Body	family
👩🏻‍❤️‍👩🏻	couple with heart: woman, woman, light skin tone	People & Body	family
👩🏻‍❤️‍👩🏼	couple with heart: woman, woman, light skin tone, medium-light skin tone	People & Body	family
👩🏻‍❤️‍👩🏽	couple with heart: woman, woman, light skin tone, medium skin tone	People & Body	family
👩🏻‍❤️‍👩🏾	couple with heart: woman, woman, light skin tone, medium-dark skin tone	People & Body	family
👩🏻‍❤️‍👩🏿	couple with heart: woman, woman, light skin tone, dark skin tone	People & Body	family
👩🏼‍❤️‍👩🏻	couple with heart: woman, woman, medium-light skin tone, light skin tone	People & Body	family
👩🏼‍❤️‍👩🏼	couple with heart: woman, woman, medium-light skin tone	People & Body	family
👩🏼‍❤️‍👩🏽	couple with heart: woman, woman, medium-light skin tone, medium skin tone	People & Body	family
👩🏼‍❤️‍👩🏾	couple with heart: woman, woman, medium-light skin tone, medium-dark skin tone	People & Body	family
👩🏼‍❤️‍👩🏿	couple with heart: woman, woman, medium-light skin tone, dark skin tone	People & Body	family
👩🏽‍❤️‍👩🏻	couple with heart: woman, woman, medium skin tone, light skin tone	People & Body	family
👩🏽‍❤️‍👩🏼	couple with heart: woman, woman, medium skin tone, medium-light skin tone	People & Body	family
👩🏽‍❤️‍👩🏽	couple with heart: woman, woman, medium skin tone	People & Body	family
👩🏽‍❤️‍👩🏾	couple with heart: woman, woman, medium skin tone, medium-dark skin tone	People & Body	family
👩🏽‍❤️‍👩🏿	couple with heart: woman, woman, medium skin tone, dark skin tone	People & Body	family
👩🏾‍❤️‍👩🏻	couple with heart: woman, woman, medium-dark skin tone, light skin tone	People & Body	family
👩🏾‍❤️‍👩🏼	couple with heart: woman, woman, medium-dark skin tone, medium-light skin tone	People & Body	family
👩🏾‍❤️‍👩🏽	couple with heart: woman, woman, medium-dark skin tone, medium skin tone	People & Body	family
👩🏾‍❤️‍👩🏾	couple with heart: woman, woman, medium-dark skin tone	People & Body	family
👩🏾‍❤️‍👩🏿	couple with heart: woman, woman, medium-dark skin tone, dark skin tone	People & Body	family
👩🏿‍❤️‍👩🏻	couple with heart: woman, woman, dark skin tone, light skin tone	People & Body	family
👩🏿‍❤️‍👩🏼	couple with heart: woman, woman, dark skin tone, medium-light skin tone	People & Body	family
👩🏿‍❤️‍👩🏽	couple with heart: woman, woman, dark skin tone, medium skin tone	People & Body	family
👩🏿‍❤️‍👩🏾	couple with heart: woman, woman, dark skin tone, medium-dark skin tone	People & Body	family
👩🏿‍❤️‍👩🏿	couple with heart: woman, woman, dark skin tone	People & Body	family
👨‍👩‍👦	family: man, woman, boy	People & Body	family
👨‍👩‍👧	family: man, woman, girl	People & Body	family
👨‍👩‍👧‍👦	family: man, woman, girl, boy	People & Body	family
👨‍👩‍👦‍👦	family: man, woman, boy, boy	People & Body	family
👨‍👩‍👧‍👧	family: man, woman, girl, girl	People & Body	family
👨‍👨‍👦	family: man, man, boy	People & Body	family
👨‍👨‍👧	family: man, man, girl	People & Body	family
👨‍👨‍👧‍👦	family: man, man, girl, boy	People & Body	family
👨‍👨‍👦‍👦	family: man, man, boy, boy	People & Body	family
👨‍👨‍👧‍👧	family: man, man, girl, girl	People & Body	family
👩‍👩‍👦	family: woman, woman, boy	People & Body	family
👩‍👩‍👧	family: woman, woman, girl	People & Body	family
👩‍👩‍👧‍👦	family: woman, woman, girl, boy	People & Body	family
👩‍👩‍👦‍👦	family: woman, woman, boy, boy	People & Body	family
👩‍👩‍👧‍👧	family: woman, woman, girl, girl	People & Body	family
👨‍👦	family: man, boy	People & Body	family
👨‍👦‍👦	family: man, boy, boy	People & Body	family
👨‍👧	family: man, girl	People & Body	family
👨‍👧‍👦	family: man, girl, boy	People & Body	family
👨‍👧‍👧	family: man, girl, girl	People & Body	family
👩‍👦	family: woman, boy	People & Body	family
👩‍👦‍👦	family: woman, boy, boy	People & Body	family
👩‍👧	family: woman, girl	People & Body	family
👩‍👧‍👦	family: woman, girl, boy	People & Body	family
👩‍👧‍👧	family: woman, girl, girl	People & Body	family
🗣️	speaking head	People & Body	person-symbol
👤	bust in silhouette	People & Body	person-symbol
👥	busts in silhouette	People & Body	person-symbol
🫂	people hugging	People & Body	person-symbol
👪	family	People & Body	person-symbol
🧑‍🧑‍🧒	family: adult, adult, child	People & Body	person-symbol
🧑‍🧑‍🧒‍🧒	family: adult, adult, child, child	People & Body	person-symbol
🧑‍🧒	family: adult, child	People & Body	person-symbol
🧑‍🧒‍🧒	family: adult, child, child	People & Body	person-symbol
👣	footprints	People & Body	person-symbol
🫆	fingerprint	People & Body	person-symbol
🐵	monkey face	Animals & Nature	animal-mammal
🐒	monkey	Animals & Nature	animal-mammal
🦍	gorilla	Animals & Nature	animal-mammal
🦧	orangutan	Animals & Nature	animal-mammal
🐶	dog face	Animals & Nature	animal-mammal
🐕	dog	Animals & Nature	animal-mammal
🦮	guide dog	Animals & Nature	animal-mammal
🐕‍🦺	service dog	Animals & Nature	animal-mammal
🐩	poodle	Animals & Nature	animal-mammal
🐺	wolf	Animals & Nature	animal-mammal
🦊	fox	Animals & Nature	animal-mammal
🦝	raccoon	Animals & Nature	animal-mammal
🐱	cat face	Animals & Nature	animal-mammal
🐈	cat	Animals & Nature	animal-mammal
🐈‍⬛	black cat	Animals & Nature	animal-mammal
🦁	lion	Animals & Nature	animal-mammal
🐯	tiger face	Animals & Nature	animal-mammal
🐅	tiger	Animals & Nature	animal-mammal
🐆	leopard	Animals & Nature	animal-mammal
🐴	horse face	Animals & Nature	animal-mammal
🫎	moose	Animals & Nature	animal-mammal
🫏	donkey	Animals & Nature	animal-mammal
🐎	horse	Animals & Nature	animal-mammal
🦄	unicorn	Animals & Nature	animal-mammal
🦓	zebra	Animals & Nature	animal-mammal
🦌	deer	Animals & Nature	animal-mammal
🦬	bison	Animals & Nature	animal-mammal
🐮	cow face	Animals & Nature	animal-mammal
🐂	ox	Animals & Nature	animal-mammal
🐃	water buffalo	Animals & Nature	animal-mammal
🐄	cow	Animals & Nature	animal-mammal
🐷	pig face	Animals & Nature	animal-mammal
🐖	pig	Animals & Nature	animal-mammal
🐗	boar	Animals & Nature	animal-mammal
🐽	pig nose	Animals & Nature	animal-mammal
🐏	ram	Animals & Nature	animal-mammal
🐑	ewe	Animals & Nature	animal-mammal
🐐	goat	Animals & Nature	animal-mammal
🐪	camel	Animals & Nature	animal-mammal
🐫	two-hump camel	Animals & Nature	animal-mammal
🦙	llama	Animals & Nature	animal-mammal
🦒	giraffe	Animals & Nature	animal-mammal
🐘	elephant	Animals & Nature	animal-mammal
🦣	mammoth	Animals & Nature	animal-mammal
🦏	rhinoceros	Animals & Nature	animal-mammal
🦛	hippopotamus	Animals & Nature	animal-mammal
🐭	mouse face	Animals & Nature	animal-mammal
🐁	mouse	Animals & Nature	animal-mammal
🐀	rat	Animals & Nature	animal-mammal
🐹	hamster	Animals & Nature	animal-mammal
🐰	rabbit face	Animals & Nature	animal-mammal
🐇	rabbit	Animals & Nature	animal-mammal
🐿️	chipmunk	Animals & Nature	animal-mammal
🦫	beaver	Animals & Nature	animal-mammal
🦔	hedgehog	Animals & Nature	animal-mammal
🦇	bat	Animals & Nature	animal-mammal
🐻	bear	Animals & Nature	animal-mammal
🐻‍❄️	polar bear	Animals & Nature	animal-mammal
🐨	koala	Animals & Nature	animal-mammal
🐼	panda	Animals & Nature	animal-mammal
🦥	sloth	Animals & Nature	animal-mammal
🦦	otter	Animals & Nature	animal-mammal
🦨	skunk	Animals & Nature	animal-mammal
🦘	kangaroo	Animals & Nature	animal-mammal
🦡	badger	Animals & Nature	animal-mammal
🐾	paw prints	Animals & Nature	animal-mammal
🦃	turkey	Animals & Nature	animal-bird
🐔	chicken	Animals & Nature	animal-bird
🐓	rooster	Animals & Nature	animal-bird
🐣	hatching chick	Animals & Nature	animal-bird
🐤	baby chick	Animals & Nature	animal-bird
🐥	front-facing baby chick	Animals & Nature	animal-bird
🐦	bird	Animals & Nature	animal-bird
🐧	penguin	Animals & Nature	animal-bird
🕊️	dove	Animals & Nature	animal-bird
🦅	eagle	Animals & Nature	animal-bird
🦆	duck	Animals & Nature	animal-bird
🦢	swan	Animals & Nature	animal-bird
🦉	owl	Animals & Nature	animal-bird
🦤	dodo	Animals & Nature	animal-bird
🪶	feather	Animals & Nature	animal-bird
🦩	flamingo	Animals & Nature	animal-bird
🦚	peacock	Animals & Nature	animal-bird
🦜	parrot	Animals & Nature	animal-bird
🪽	wing	Animals & Nature	animal-bird
🐦‍⬛	black bird	Animals & Nature	animal-bird
🪿	goose	Animals & Nature	animal-bird
🐦‍🔥	phoenix	Animals & Nature	animal-bird
🐸	frog	Animals & Nature	animal-amphibian
🐊	crocodile	Animals & Nature	animal-reptile
🐢	turtle	Animals & Nature	animal-reptile
🦎	lizard	Animals & Nature	animal-reptile
🐍	snake	Animals & Nature	animal-reptile
🐲	dragon face	Animals & Nature	animal-reptile
🐉	dragon	Animals & Nature	animal-reptile
🦕	sauropod	Animals & Nature	animal-reptile
🦖	T-Rex	Animals & Nature	animal-reptile
🐳	spouting whale	Animals & Nature	animal-marine
🐋	whale	Animals & Nature	animal-marine
🐬	dolphin	Animals & Nature	animal-marine
🫍	orca	Animals & Nature	animal-marine
🦭	seal	Animals & Nature	animal-marine
🐟	fish	Animals & Nature	animal-marine
🐠	tropical fish	Animals & Nature	animal-marine
🐡	blowfish	Animals & Nature	animal-marine
🦈	shark	Animals & Nature	animal-marine
🐙	octopus	Animals & Nature	animal-marine
🐚	spiral shell	Animals & Nature	animal-marine
🪸	coral	Animals & Nature	animal-marine
🪼	jellyfish	Animals & Nature	animal-marine
🦀	crab	Animals & Nature	animal-marine
🦞	lobster	Animals & Nature	animal-marine
🦐	shrimp	Animals & Nature	animal-marine
🦑	squid	Animals & Nature	animal-marine
🦪	oyster	Animals & Nature	animal-marine
🐌	snail	Animals & Nature	animal-bug
🦋	butterfly	Animals & Nature	animal-bug
🐛	bug	Animals & Nature	animal-bug
🐜	ant	Animals & Nature	animal-bug
🐝	honeybee	Animals & Nature	animal-bug
🪲	beetle	Animals & Nature	animal-bug
🐞	lady beetle	Animals & Nature	animal-bug
🦗	cricket	Animals & Nature	animal-bug
🪳	cockroach	Animals & Nature	animal-bug
🕷️	spider	Animals & Nature	animal-bug
🕸️	spider web	Animals & Nature	animal-bug
🦂	scorpion	Animals & Nature	animal-bug
🦟	mosquito	Animals & Nature	animal-bug
🪰	fly	Animals & Nature	animal-bug
🪱	worm	Animals & Nature	animal-bug
🦠	microbe	Animals & Nature	animal-bug
💐	bouquet	Animals & Nature	plant-flower
🌸	cherry blossom	Animals & Nature	plant-flower
"""

private const val EMOJI_DATA_4 = """
💮	white flower	Animals & Nature	plant-flower
🪷	lotus	Animals & Nature	plant-flower
🏵️	rosette	Animals & Nature	plant-flower
🌹	rose	Animals & Nature	plant-flower
🥀	wilted flower	Animals & Nature	plant-flower
🌺	hibiscus	Animals & Nature	plant-flower
🌻	sunflower	Animals & Nature	plant-flower
🌼	blossom	Animals & Nature	plant-flower
🌷	tulip	Animals & Nature	plant-flower
🪻	hyacinth	Animals & Nature	plant-flower
🌱	seedling	Animals & Nature	plant-other
🪴	potted plant	Animals & Nature	plant-other
🌲	evergreen tree	Animals & Nature	plant-other
🌳	deciduous tree	Animals & Nature	plant-other
🌴	palm tree	Animals & Nature	plant-other
🌵	cactus	Animals & Nature	plant-other
🌾	sheaf of rice	Animals & Nature	plant-other
🌿	herb	Animals & Nature	plant-other
☘️	shamrock	Animals & Nature	plant-other
🍀	four leaf clover	Animals & Nature	plant-other
🍁	maple leaf	Animals & Nature	plant-other
🍂	fallen leaf	Animals & Nature	plant-other
🍃	leaf fluttering in wind	Animals & Nature	plant-other
🪹	empty nest	Animals & Nature	plant-other
🪺	nest with eggs	Animals & Nature	plant-other
🍄	mushroom	Animals & Nature	plant-other
🪾	leafless tree	Animals & Nature	plant-other
🍇	grapes	Food & Drink	food-fruit
🍈	melon	Food & Drink	food-fruit
🍉	watermelon	Food & Drink	food-fruit
🍊	tangerine	Food & Drink	food-fruit
🍋	lemon	Food & Drink	food-fruit
🍋‍🟩	lime	Food & Drink	food-fruit
🍌	banana	Food & Drink	food-fruit
🍍	pineapple	Food & Drink	food-fruit
🥭	mango	Food & Drink	food-fruit
🍎	red apple	Food & Drink	food-fruit
🍏	green apple	Food & Drink	food-fruit
🍐	pear	Food & Drink	food-fruit
🍑	peach	Food & Drink	food-fruit
🍒	cherries	Food & Drink	food-fruit
🍓	strawberry	Food & Drink	food-fruit
🫐	blueberries	Food & Drink	food-fruit
🥝	kiwi fruit	Food & Drink	food-fruit
🍅	tomato	Food & Drink	food-fruit
🫒	olive	Food & Drink	food-fruit
🥥	coconut	Food & Drink	food-fruit
🥑	avocado	Food & Drink	food-vegetable
🍆	eggplant	Food & Drink	food-vegetable
🥔	potato	Food & Drink	food-vegetable
🥕	carrot	Food & Drink	food-vegetable
🌽	ear of corn	Food & Drink	food-vegetable
🌶️	hot pepper	Food & Drink	food-vegetable
🫑	bell pepper	Food & Drink	food-vegetable
🥒	cucumber	Food & Drink	food-vegetable
🥬	leafy green	Food & Drink	food-vegetable
🥦	broccoli	Food & Drink	food-vegetable
🧄	garlic	Food & Drink	food-vegetable
🧅	onion	Food & Drink	food-vegetable
🥜	peanuts	Food & Drink	food-vegetable
🫘	beans	Food & Drink	food-vegetable
🌰	chestnut	Food & Drink	food-vegetable
🫚	ginger root	Food & Drink	food-vegetable
🫛	pea pod	Food & Drink	food-vegetable
🍄‍🟫	brown mushroom	Food & Drink	food-vegetable
🫜	root vegetable	Food & Drink	food-vegetable
🍞	bread	Food & Drink	food-prepared
🥐	croissant	Food & Drink	food-prepared
🥖	baguette bread	Food & Drink	food-prepared
🫓	flatbread	Food & Drink	food-prepared
🥨	pretzel	Food & Drink	food-prepared
🥯	bagel	Food & Drink	food-prepared
🥞	pancakes	Food & Drink	food-prepared
🧇	waffle	Food & Drink	food-prepared
🧀	cheese wedge	Food & Drink	food-prepared
🍖	meat on bone	Food & Drink	food-prepared
🍗	poultry leg	Food & Drink	food-prepared
🥩	cut of meat	Food & Drink	food-prepared
🥓	bacon	Food & Drink	food-prepared
🍔	hamburger	Food & Drink	food-prepared
🍟	french fries	Food & Drink	food-prepared
🍕	pizza	Food & Drink	food-prepared
🌭	hot dog	Food & Drink	food-prepared
🥪	sandwich	Food & Drink	food-prepared
🌮	taco	Food & Drink	food-prepared
🌯	burrito	Food & Drink	food-prepared
🫔	tamale	Food & Drink	food-prepared
🥙	stuffed flatbread	Food & Drink	food-prepared
🧆	falafel	Food & Drink	food-prepared
🥚	egg	Food & Drink	food-prepared
🍳	cooking	Food & Drink	food-prepared
🥘	shallow pan of food	Food & Drink	food-prepared
🍲	pot of food	Food & Drink	food-prepared
🫕	fondue	Food & Drink	food-prepared
🥣	bowl with spoon	Food & Drink	food-prepared
🥗	green salad	Food & Drink	food-prepared
🍿	popcorn	Food & Drink	food-prepared
🧈	butter	Food & Drink	food-prepared
🧂	salt	Food & Drink	food-prepared
🥫	canned food	Food & Drink	food-prepared
🍱	bento box	Food & Drink	food-asian
🍘	rice cracker	Food & Drink	food-asian
🍙	rice ball	Food & Drink	food-asian
🍚	cooked rice	Food & Drink	food-asian
🍛	curry rice	Food & Drink	food-asian
🍜	steaming bowl	Food & Drink	food-asian
🍝	spaghetti	Food & Drink	food-asian
🍠	roasted sweet potato	Food & Drink	food-asian
🍢	oden	Food & Drink	food-asian
🍣	sushi	Food & Drink	food-asian
🍤	fried shrimp	Food & Drink	food-asian
🍥	fish cake with swirl	Food & Drink	food-asian
🥮	moon cake	Food & Drink	food-asian
🍡	dango	Food & Drink	food-asian
🥟	dumpling	Food & Drink	food-asian
🥠	fortune cookie	Food & Drink	food-asian
🥡	takeout box	Food & Drink	food-asian
🍦	soft ice cream	Food & Drink	food-sweet
🍧	shaved ice	Food & Drink	food-sweet
🍨	ice cream	Food & Drink	food-sweet
🍩	doughnut	Food & Drink	food-sweet
🍪	cookie	Food & Drink	food-sweet
🎂	birthday cake	Food & Drink	food-sweet
🍰	shortcake	Food & Drink	food-sweet
🧁	cupcake	Food & Drink	food-sweet
🥧	pie	Food & Drink	food-sweet
🍫	chocolate bar	Food & Drink	food-sweet
🍬	candy	Food & Drink	food-sweet
🍭	lollipop	Food & Drink	food-sweet
🍮	custard	Food & Drink	food-sweet
🍯	honey pot	Food & Drink	food-sweet
🍼	baby bottle	Food & Drink	drink
🥛	glass of milk	Food & Drink	drink
☕	hot beverage	Food & Drink	drink
🫖	teapot	Food & Drink	drink
🍵	teacup without handle	Food & Drink	drink
🍶	sake	Food & Drink	drink
🍾	bottle with popping cork	Food & Drink	drink
🍷	wine glass	Food & Drink	drink
🍸	cocktail glass	Food & Drink	drink
🍹	tropical drink	Food & Drink	drink
🍺	beer mug	Food & Drink	drink
🍻	clinking beer mugs	Food & Drink	drink
🥂	clinking glasses	Food & Drink	drink
🥃	tumbler glass	Food & Drink	drink
🫗	pouring liquid	Food & Drink	drink
🥤	cup with straw	Food & Drink	drink
🧋	bubble tea	Food & Drink	drink
🧃	beverage box	Food & Drink	drink
🧉	mate	Food & Drink	drink
🧊	ice	Food & Drink	drink
🥢	chopsticks	Food & Drink	dishware
🍽️	fork and knife with plate	Food & Drink	dishware
🍴	fork and knife	Food & Drink	dishware
🥄	spoon	Food & Drink	dishware
🔪	kitchen knife	Food & Drink	dishware
🫙	jar	Food & Drink	dishware
🏺	amphora	Food & Drink	dishware
🌍	globe showing Europe-Africa	Travel & Places	place-map
🌎	globe showing Americas	Travel & Places	place-map
🌏	globe showing Asia-Australia	Travel & Places	place-map
🌐	globe with meridians	Travel & Places	place-map
🗺️	world map	Travel & Places	place-map
🗾	map of Japan	Travel & Places	place-map
🧭	compass	Travel & Places	place-map
🏔️	snow-capped mountain	Travel & Places	place-geographic
⛰️	mountain	Travel & Places	place-geographic
🛘	landslide	Travel & Places	place-geographic
🌋	volcano	Travel & Places	place-geographic
🗻	mount fuji	Travel & Places	place-geographic
🏕️	camping	Travel & Places	place-geographic
🏖️	beach with umbrella	Travel & Places	place-geographic
🏜️	desert	Travel & Places	place-geographic
🏝️	desert island	Travel & Places	place-geographic
🏞️	national park	Travel & Places	place-geographic
🏟️	stadium	Travel & Places	place-building
🏛️	classical building	Travel & Places	place-building
🏗️	building construction	Travel & Places	place-building
🧱	brick	Travel & Places	place-building
🪨	rock	Travel & Places	place-building
🪵	wood	Travel & Places	place-building
🛖	hut	Travel & Places	place-building
🏘️	houses	Travel & Places	place-building
🏚️	derelict house	Travel & Places	place-building
🏠	house	Travel & Places	place-building
🏡	house with garden	Travel & Places	place-building
🏢	office building	Travel & Places	place-building
🏣	Japanese post office	Travel & Places	place-building
🏤	post office	Travel & Places	place-building
🏥	hospital	Travel & Places	place-building
🏦	bank	Travel & Places	place-building
🏨	hotel	Travel & Places	place-building
🏩	love hotel	Travel & Places	place-building
🏪	convenience store	Travel & Places	place-building
🏫	school	Travel & Places	place-building
🏬	department store	Travel & Places	place-building
🏭	factory	Travel & Places	place-building
🏯	Japanese castle	Travel & Places	place-building
🏰	castle	Travel & Places	place-building
💒	wedding	Travel & Places	place-building
🗼	Tokyo tower	Travel & Places	place-building
🗽	Statue of Liberty	Travel & Places	place-building
⛪	church	Travel & Places	place-religious
🕌	mosque	Travel & Places	place-religious
🛕	hindu temple	Travel & Places	place-religious
🕍	synagogue	Travel & Places	place-religious
⛩️	shinto shrine	Travel & Places	place-religious
🕋	kaaba	Travel & Places	place-religious
⛲	fountain	Travel & Places	place-other
⛺	tent	Travel & Places	place-other
🌁	foggy	Travel & Places	place-other
🌃	night with stars	Travel & Places	place-other
🏙️	cityscape	Travel & Places	place-other
🌄	sunrise over mountains	Travel & Places	place-other
🌅	sunrise	Travel & Places	place-other
🌆	cityscape at dusk	Travel & Places	place-other
🌇	sunset	Travel & Places	place-other
🌉	bridge at night	Travel & Places	place-other
♨️	hot springs	Travel & Places	place-other
🎠	carousel horse	Travel & Places	place-other
🛝	playground slide	Travel & Places	place-other
🎡	ferris wheel	Travel & Places	place-other
🎢	roller coaster	Travel & Places	place-other
💈	barber pole	Travel & Places	place-other
🎪	circus tent	Travel & Places	place-other
🚂	locomotive	Travel & Places	transport-ground
🚃	railway car	Travel & Places	transport-ground
🚄	high-speed train	Travel & Places	transport-ground
🚅	bullet train	Travel & Places	transport-ground
🚆	train	Travel & Places	transport-ground
🚇	metro	Travel & Places	transport-ground
🚈	light rail	Travel & Places	transport-ground
🚉	station	Travel & Places	transport-ground
🚊	tram	Travel & Places	transport-ground
🚝	monorail	Travel & Places	transport-ground
🚞	mountain railway	Travel & Places	transport-ground
🚋	tram car	Travel & Places	transport-ground
🚌	bus	Travel & Places	transport-ground
🚍	oncoming bus	Travel & Places	transport-ground
🚎	trolleybus	Travel & Places	transport-ground
🚐	minibus	Travel & Places	transport-ground
🚑	ambulance	Travel & Places	transport-ground
🚒	fire engine	Travel & Places	transport-ground
🚓	police car	Travel & Places	transport-ground
🚔	oncoming police car	Travel & Places	transport-ground
🚕	taxi	Travel & Places	transport-ground
🚖	oncoming taxi	Travel & Places	transport-ground
🚗	automobile	Travel & Places	transport-ground
🚘	oncoming automobile	Travel & Places	transport-ground
🚙	sport utility vehicle	Travel & Places	transport-ground
🛻	pickup truck	Travel & Places	transport-ground
🚚	delivery truck	Travel & Places	transport-ground
🚛	articulated lorry	Travel & Places	transport-ground
🚜	tractor	Travel & Places	transport-ground
🏎️	racing car	Travel & Places	transport-ground
🏍️	motorcycle	Travel & Places	transport-ground
🛵	motor scooter	Travel & Places	transport-ground
🦽	manual wheelchair	Travel & Places	transport-ground
🦼	motorized wheelchair	Travel & Places	transport-ground
🛺	auto rickshaw	Travel & Places	transport-ground
🚲	bicycle	Travel & Places	transport-ground
🛴	kick scooter	Travel & Places	transport-ground
🛹	skateboard	Travel & Places	transport-ground
🛼	roller skate	Travel & Places	transport-ground
🚏	bus stop	Travel & Places	transport-ground
🛣️	motorway	Travel & Places	transport-ground
🛤️	railway track	Travel & Places	transport-ground
🛢️	oil drum	Travel & Places	transport-ground
⛽	fuel pump	Travel & Places	transport-ground
🛞	wheel	Travel & Places	transport-ground
🚨	police car light	Travel & Places	transport-ground
🚥	horizontal traffic light	Travel & Places	transport-ground
🚦	vertical traffic light	Travel & Places	transport-ground
🛑	stop sign	Travel & Places	transport-ground
🚧	construction	Travel & Places	transport-ground
⚓	anchor	Travel & Places	transport-water
🛟	ring buoy	Travel & Places	transport-water
⛵	sailboat	Travel & Places	transport-water
🛶	canoe	Travel & Places	transport-water
🚤	speedboat	Travel & Places	transport-water
🛳️	passenger ship	Travel & Places	transport-water
⛴️	ferry	Travel & Places	transport-water
🛥️	motor boat	Travel & Places	transport-water
🚢	ship	Travel & Places	transport-water
✈️	airplane	Travel & Places	transport-air
🛩️	small airplane	Travel & Places	transport-air
🛫	airplane departure	Travel & Places	transport-air
🛬	airplane arrival	Travel & Places	transport-air
🪂	parachute	Travel & Places	transport-air
💺	seat	Travel & Places	transport-air
🚁	helicopter	Travel & Places	transport-air
🚟	suspension railway	Travel & Places	transport-air
🚠	mountain cableway	Travel & Places	transport-air
🚡	aerial tramway	Travel & Places	transport-air
🛰️	satellite	Travel & Places	transport-air
🚀	rocket	Travel & Places	transport-air
🛸	flying saucer	Travel & Places	transport-air
🛎️	bellhop bell	Travel & Places	hotel
🧳	luggage	Travel & Places	hotel
⌛	hourglass done	Travel & Places	time
⏳	hourglass not done	Travel & Places	time
⌚	watch	Travel & Places	time
⏰	alarm clock	Travel & Places	time
⏱️	stopwatch	Travel & Places	time
⏲️	timer clock	Travel & Places	time
🕰️	mantelpiece clock	Travel & Places	time
🕛	twelve o’clock	Travel & Places	time
🕧	twelve-thirty	Travel & Places	time
🕐	one o’clock	Travel & Places	time
🕜	one-thirty	Travel & Places	time
🕑	two o’clock	Travel & Places	time
🕝	two-thirty	Travel & Places	time
🕒	three o’clock	Travel & Places	time
🕞	three-thirty	Travel & Places	time
🕓	four o’clock	Travel & Places	time
🕟	four-thirty	Travel & Places	time
🕔	five o’clock	Travel & Places	time
🕠	five-thirty	Travel & Places	time
🕕	six o’clock	Travel & Places	time
🕡	six-thirty	Travel & Places	time
🕖	seven o’clock	Travel & Places	time
🕢	seven-thirty	Travel & Places	time
🕗	eight o’clock	Travel & Places	time
🕣	eight-thirty	Travel & Places	time
🕘	nine o’clock	Travel & Places	time
🕤	nine-thirty	Travel & Places	time
🕙	ten o’clock	Travel & Places	time
🕥	ten-thirty	Travel & Places	time
🕚	eleven o’clock	Travel & Places	time
🕦	eleven-thirty	Travel & Places	time
🌑	new moon	Travel & Places	sky & weather
🌒	waxing crescent moon	Travel & Places	sky & weather
🌓	first quarter moon	Travel & Places	sky & weather
🌔	waxing gibbous moon	Travel & Places	sky & weather
🌕	full moon	Travel & Places	sky & weather
🌖	waning gibbous moon	Travel & Places	sky & weather
🌗	last quarter moon	Travel & Places	sky & weather
🌘	waning crescent moon	Travel & Places	sky & weather
🌙	crescent moon	Travel & Places	sky & weather
🌚	new moon face	Travel & Places	sky & weather
🌛	first quarter moon face	Travel & Places	sky & weather
🌜	last quarter moon face	Travel & Places	sky & weather
🌡️	thermometer	Travel & Places	sky & weather
☀️	sun	Travel & Places	sky & weather
🌝	full moon face	Travel & Places	sky & weather
🌞	sun with face	Travel & Places	sky & weather
🪐	ringed planet	Travel & Places	sky & weather
⭐	star	Travel & Places	sky & weather
🌟	glowing star	Travel & Places	sky & weather
🌠	shooting star	Travel & Places	sky & weather
🌌	milky way	Travel & Places	sky & weather
☁️	cloud	Travel & Places	sky & weather
⛅	sun behind cloud	Travel & Places	sky & weather
⛈️	cloud with lightning and rain	Travel & Places	sky & weather
🌤️	sun behind small cloud	Travel & Places	sky & weather
🌥️	sun behind large cloud	Travel & Places	sky & weather
🌦️	sun behind rain cloud	Travel & Places	sky & weather
🌧️	cloud with rain	Travel & Places	sky & weather
🌨️	cloud with snow	Travel & Places	sky & weather
🌩️	cloud with lightning	Travel & Places	sky & weather
🌪️	tornado	Travel & Places	sky & weather
🌫️	fog	Travel & Places	sky & weather
🌬️	wind face	Travel & Places	sky & weather
🌀	cyclone	Travel & Places	sky & weather
🌈	rainbow	Travel & Places	sky & weather
🌂	closed umbrella	Travel & Places	sky & weather
☂️	umbrella	Travel & Places	sky & weather
☔	umbrella with rain drops	Travel & Places	sky & weather
⛱️	umbrella on ground	Travel & Places	sky & weather
⚡	high voltage	Travel & Places	sky & weather
❄️	snowflake	Travel & Places	sky & weather
☃️	snowman	Travel & Places	sky & weather
⛄	snowman without snow	Travel & Places	sky & weather
☄️	comet	Travel & Places	sky & weather
🔥	fire	Travel & Places	sky & weather
💧	droplet	Travel & Places	sky & weather
🌊	water wave	Travel & Places	sky & weather
🎃	jack-o-lantern	Activities	event
🎄	Christmas tree	Activities	event
🎆	fireworks	Activities	event
🎇	sparkler	Activities	event
🧨	firecracker	Activities	event
✨	sparkles	Activities	event
🎈	balloon	Activities	event
🎉	party popper	Activities	event
🎊	confetti ball	Activities	event
🎋	tanabata tree	Activities	event
🎍	pine decoration	Activities	event
🎎	Japanese dolls	Activities	event
🎏	carp streamer	Activities	event
🎐	wind chime	Activities	event
🎑	moon viewing ceremony	Activities	event
🧧	red envelope	Activities	event
🎀	ribbon	Activities	event
🎁	wrapped gift	Activities	event
🎗️	reminder ribbon	Activities	event
🎟️	admission tickets	Activities	event
🎫	ticket	Activities	event
🎖️	military medal	Activities	award-medal
🏆	trophy	Activities	award-medal
🏅	sports medal	Activities	award-medal
🥇	1st place medal	Activities	award-medal
🥈	2nd place medal	Activities	award-medal
🥉	3rd place medal	Activities	award-medal
⚽	soccer ball	Activities	sport
⚾	baseball	Activities	sport
🥎	softball	Activities	sport
🏀	basketball	Activities	sport
🏐	volleyball	Activities	sport
🏈	american football	Activities	sport
🏉	rugby football	Activities	sport
🎾	tennis	Activities	sport
🥏	flying disc	Activities	sport
🎳	bowling	Activities	sport
🏏	cricket game	Activities	sport
🏑	field hockey	Activities	sport
🏒	ice hockey	Activities	sport
🥍	lacrosse	Activities	sport
🏓	ping pong	Activities	sport
🏸	badminton	Activities	sport
🥊	boxing glove	Activities	sport
🥋	martial arts uniform	Activities	sport
🥅	goal net	Activities	sport
⛳	flag in hole	Activities	sport
⛸️	ice skate	Activities	sport
🎣	fishing pole	Activities	sport
🤿	diving mask	Activities	sport
🎽	running shirt	Activities	sport
🎿	skis	Activities	sport
🛷	sled	Activities	sport
🥌	curling stone	Activities	sport
🎯	bullseye	Activities	game
🪀	yo-yo	Activities	game
🪁	kite	Activities	game
🔫	water pistol	Activities	game
🎱	pool 8 ball	Activities	game
🔮	crystal ball	Activities	game
🪄	magic wand	Activities	game
🎮	video game	Activities	game
🕹️	joystick	Activities	game
🎰	slot machine	Activities	game
🎲	game die	Activities	game
🧩	puzzle piece	Activities	game
🧸	teddy bear	Activities	game
🪅	piñata	Activities	game
🪩	mirror ball	Activities	game
🪆	nesting dolls	Activities	game
♠️	spade suit	Activities	game
♥️	heart suit	Activities	game
♦️	diamond suit	Activities	game
♣️	club suit	Activities	game
♟️	chess pawn	Activities	game
🃏	joker	Activities	game
🀄	mahjong red dragon	Activities	game
🎴	flower playing cards	Activities	game
🎭	performing arts	Activities	arts & crafts
🖼️	framed picture	Activities	arts & crafts
🎨	artist palette	Activities	arts & crafts
🧵	thread	Activities	arts & crafts
🪡	sewing needle	Activities	arts & crafts
🧶	yarn	Activities	arts & crafts
🪢	knot	Activities	arts & crafts
👓	glasses	Objects	clothing
🕶️	sunglasses	Objects	clothing
🥽	goggles	Objects	clothing
🥼	lab coat	Objects	clothing
🦺	safety vest	Objects	clothing
👔	necktie	Objects	clothing
👕	t-shirt	Objects	clothing
👖	jeans	Objects	clothing
🧣	scarf	Objects	clothing
🧤	gloves	Objects	clothing
🧥	coat	Objects	clothing
🧦	socks	Objects	clothing
👗	dress	Objects	clothing
👘	kimono	Objects	clothing
🥻	sari	Objects	clothing
🩱	one-piece swimsuit	Objects	clothing
🩲	briefs	Objects	clothing
🩳	shorts	Objects	clothing
👙	bikini	Objects	clothing
👚	woman’s clothes	Objects	clothing
🪭	folding hand fan	Objects	clothing
👛	purse	Objects	clothing
👜	handbag	Objects	clothing
👝	clutch bag	Objects	clothing
🛍️	shopping bags	Objects	clothing
🎒	backpack	Objects	clothing
🩴	thong sandal	Objects	clothing
👞	man’s shoe	Objects	clothing
👟	running shoe	Objects	clothing
🥾	hiking boot	Objects	clothing
🥿	flat shoe	Objects	clothing
👠	high-heeled shoe	Objects	clothing
👡	woman’s sandal	Objects	clothing
🩰	ballet shoes	Objects	clothing
👢	woman’s boot	Objects	clothing
🪮	hair pick	Objects	clothing
👑	crown	Objects	clothing
👒	woman’s hat	Objects	clothing
🎩	top hat	Objects	clothing
🎓	graduation cap	Objects	clothing
🧢	billed cap	Objects	clothing
🪖	military helmet	Objects	clothing
⛑️	rescue worker’s helmet	Objects	clothing
📿	prayer beads	Objects	clothing
💄	lipstick	Objects	clothing
💍	ring	Objects	clothing
💎	gem stone	Objects	clothing
🔇	muted speaker	Objects	sound
🔈	speaker low volume	Objects	sound
🔉	speaker medium volume	Objects	sound
🔊	speaker high volume	Objects	sound
📢	loudspeaker	Objects	sound
📣	megaphone	Objects	sound
📯	postal horn	Objects	sound
🔔	bell	Objects	sound
🔕	bell with slash	Objects	sound
🎼	musical score	Objects	music
🎵	musical note	Objects	music
🎶	musical notes	Objects	music
🎙️	studio microphone	Objects	music
🎚️	level slider	Objects	music
🎛️	control knobs	Objects	music
🎤	microphone	Objects	music
🎧	headphone	Objects	music
📻	radio	Objects	music
🎷	saxophone	Objects	musical-instrument
🎺	trumpet	Objects	musical-instrument
🪊	trombone	Objects	musical-instrument
🪗	accordion	Objects	musical-instrument
🎸	guitar	Objects	musical-instrument
🎹	musical keyboard	Objects	musical-instrument
🎻	violin	Objects	musical-instrument
🪕	banjo	Objects	musical-instrument
🥁	drum	Objects	musical-instrument
🪘	long drum	Objects	musical-instrument
🪇	maracas	Objects	musical-instrument
🪈	flute	Objects	musical-instrument
🪉	harp	Objects	musical-instrument
📱	mobile phone	Objects	phone
📲	mobile phone with arrow	Objects	phone
☎️	telephone	Objects	phone
📞	telephone receiver	Objects	phone
📟	pager	Objects	phone
📠	fax machine	Objects	phone
🔋	battery	Objects	computer
🪫	low battery	Objects	computer
🔌	electric plug	Objects	computer
💻	laptop	Objects	computer
🖥️	desktop computer	Objects	computer
🖨️	printer	Objects	computer
⌨️	keyboard	Objects	computer
🖱️	computer mouse	Objects	computer
🖲️	trackball	Objects	computer
💽	computer disk	Objects	computer
💾	floppy disk	Objects	computer
💿	optical disk	Objects	computer
📀	dvd	Objects	computer
🧮	abacus	Objects	computer
🎥	movie camera	Objects	light & video
🎞️	film frames	Objects	light & video
📽️	film projector	Objects	light & video
🎬	clapper board	Objects	light & video
📺	television	Objects	light & video
📷	camera	Objects	light & video
📸	camera with flash	Objects	light & video
📹	video camera	Objects	light & video
📼	videocassette	Objects	light & video
🔍	magnifying glass tilted left	Objects	light & video
🔎	magnifying glass tilted right	Objects	light & video
🕯️	candle	Objects	light & video
💡	light bulb	Objects	light & video
🔦	flashlight	Objects	light & video
🏮	red paper lantern	Objects	light & video
🪔	diya lamp	Objects	light & video
📔	notebook with decorative cover	Objects	book-paper
📕	closed book	Objects	book-paper
📖	open book	Objects	book-paper
📗	green book	Objects	book-paper
📘	blue book	Objects	book-paper
📙	orange book	Objects	book-paper
📚	books	Objects	book-paper
📓	notebook	Objects	book-paper
📒	ledger	Objects	book-paper
📃	page with curl	Objects	book-paper
📜	scroll	Objects	book-paper
📄	page facing up	Objects	book-paper
📰	newspaper	Objects	book-paper
🗞️	rolled-up newspaper	Objects	book-paper
📑	bookmark tabs	Objects	book-paper
🔖	bookmark	Objects	book-paper
🏷️	label	Objects	book-paper
🪙	coin	Objects	money
💰	money bag	Objects	money
🪎	treasure chest	Objects	money
💴	yen banknote	Objects	money
💵	dollar banknote	Objects	money
💶	euro banknote	Objects	money
💷	pound banknote	Objects	money
💸	money with wings	Objects	money
💳	credit card	Objects	money
🧾	receipt	Objects	money
💹	chart increasing with yen	Objects	money
✉️	envelope	Objects	mail
📧	e-mail	Objects	mail
📨	incoming envelope	Objects	mail
📩	envelope with arrow	Objects	mail
📤	outbox tray	Objects	mail
📥	inbox tray	Objects	mail
📦	package	Objects	mail
📫	closed mailbox with raised flag	Objects	mail
📪	closed mailbox with lowered flag	Objects	mail
📬	open mailbox with raised flag	Objects	mail
📭	open mailbox with lowered flag	Objects	mail
📮	postbox	Objects	mail
🗳️	ballot box with ballot	Objects	mail
✏️	pencil	Objects	writing
✒️	black nib	Objects	writing
🖋️	fountain pen	Objects	writing
🖊️	pen	Objects	writing
🖌️	paintbrush	Objects	writing
🖍️	crayon	Objects	writing
📝	memo	Objects	writing
💼	briefcase	Objects	office
📁	file folder	Objects	office
📂	open file folder	Objects	office
🗂️	card index dividers	Objects	office
📅	calendar	Objects	office
📆	tear-off calendar	Objects	office
🗒️	spiral notepad	Objects	office
🗓️	spiral calendar	Objects	office
📇	card index	Objects	office
📈	chart increasing	Objects	office
📉	chart decreasing	Objects	office
📊	bar chart	Objects	office
📋	clipboard	Objects	office
📌	pushpin	Objects	office
📍	round pushpin	Objects	office
📎	paperclip	Objects	office
🖇️	linked paperclips	Objects	office
📏	straight ruler	Objects	office
📐	triangular ruler	Objects	office
✂️	scissors	Objects	office
🗃️	card file box	Objects	office
🗄️	file cabinet	Objects	office
🗑️	wastebasket	Objects	office
🔒	locked	Objects	lock
🔓	unlocked	Objects	lock
🔏	locked with pen	Objects	lock
🔐	locked with key	Objects	lock
🔑	key	Objects	lock
🗝️	old key	Objects	lock
🔨	hammer	Objects	tool
🪓	axe	Objects	tool
⛏️	pick	Objects	tool
⚒️	hammer and pick	Objects	tool
🛠️	hammer and wrench	Objects	tool
🗡️	dagger	Objects	tool
⚔️	crossed swords	Objects	tool
💣	bomb	Objects	tool
🪃	boomerang	Objects	tool
🏹	bow and arrow	Objects	tool
🛡️	shield	Objects	tool
🪚	carpentry saw	Objects	tool
🔧	wrench	Objects	tool
🪛	screwdriver	Objects	tool
🔩	nut and bolt	Objects	tool
⚙️	gear	Objects	tool
🗜️	clamp	Objects	tool
⚖️	balance scale	Objects	tool
🦯	white cane	Objects	tool
🔗	link	Objects	tool
⛓️‍💥	broken chain	Objects	tool
⛓️	chains	Objects	tool
🪝	hook	Objects	tool
🧰	toolbox	Objects	tool
🧲	magnet	Objects	tool
🪜	ladder	Objects	tool
🪏	shovel	Objects	tool
⚗️	alembic	Objects	science
🧪	test tube	Objects	science
🧫	petri dish	Objects	science
🧬	dna	Objects	science
🔬	microscope	Objects	science
🔭	telescope	Objects	science
📡	satellite antenna	Objects	science
💉	syringe	Objects	medical
🩸	drop of blood	Objects	medical
💊	pill	Objects	medical
🩹	adhesive bandage	Objects	medical
🩼	crutch	Objects	medical
🩺	stethoscope	Objects	medical
🩻	x-ray	Objects	medical
🚪	door	Objects	household
🛗	elevator	Objects	household
🪞	mirror	Objects	household
🪟	window	Objects	household
🛏️	bed	Objects	household
🛋️	couch and lamp	Objects	household
🪑	chair	Objects	household
🚽	toilet	Objects	household
🪠	plunger	Objects	household
🚿	shower	Objects	household
🛁	bathtub	Objects	household
🪤	mouse trap	Objects	household
🪒	razor	Objects	household
🧴	lotion bottle	Objects	household
🧷	safety pin	Objects	household
🧹	broom	Objects	household
🧺	basket	Objects	household
🧻	roll of paper	Objects	household
🪣	bucket	Objects	household
🧼	soap	Objects	household
🫧	bubbles	Objects	household
🪥	toothbrush	Objects	household
🧽	sponge	Objects	household
🧯	fire extinguisher	Objects	household
🛒	shopping cart	Objects	household
🚬	cigarette	Objects	other-object
⚰️	coffin	Objects	other-object
🪦	headstone	Objects	other-object
⚱️	funeral urn	Objects	other-object
🧿	nazar amulet	Objects	other-object
🪬	hamsa	Objects	other-object
🗿	moai	Objects	other-object
🪧	placard	Objects	other-object
🪪	identification card	Objects	other-object
🏧	ATM sign	Symbols	transport-sign
🚮	litter in bin sign	Symbols	transport-sign
🚰	potable water	Symbols	transport-sign
♿	wheelchair symbol	Symbols	transport-sign
🚹	men’s room	Symbols	transport-sign
🚺	women’s room	Symbols	transport-sign
🚻	restroom	Symbols	transport-sign
🚼	baby symbol	Symbols	transport-sign
🚾	water closet	Symbols	transport-sign
🛂	passport control	Symbols	transport-sign
🛃	customs	Symbols	transport-sign
🛄	baggage claim	Symbols	transport-sign
🛅	left luggage	Symbols	transport-sign
⚠️	warning	Symbols	warning
🚸	children crossing	Symbols	warning
⛔	no entry	Symbols	warning
🚫	prohibited	Symbols	warning
🚳	no bicycles	Symbols	warning
🚭	no smoking	Symbols	warning
🚯	no littering	Symbols	warning
🚱	non-potable water	Symbols	warning
🚷	no pedestrians	Symbols	warning
📵	no mobile phones	Symbols	warning
🔞	no one under eighteen	Symbols	warning
☢️	radioactive	Symbols	warning
☣️	biohazard	Symbols	warning
⬆️	up arrow	Symbols	arrow
↗️	up-right arrow	Symbols	arrow
➡️	right arrow	Symbols	arrow
↘️	down-right arrow	Symbols	arrow
⬇️	down arrow	Symbols	arrow
↙️	down-left arrow	Symbols	arrow
⬅️	left arrow	Symbols	arrow
↖️	up-left arrow	Symbols	arrow
↕️	up-down arrow	Symbols	arrow
↔️	left-right arrow	Symbols	arrow
↩️	right arrow curving left	Symbols	arrow
↪️	left arrow curving right	Symbols	arrow
⤴️	right arrow curving up	Symbols	arrow
⤵️	right arrow curving down	Symbols	arrow
🔃	clockwise vertical arrows	Symbols	arrow
🔄	counterclockwise arrows button	Symbols	arrow
🔙	BACK arrow	Symbols	arrow
🔚	END arrow	Symbols	arrow
🔛	ON! arrow	Symbols	arrow
🔜	SOON arrow	Symbols	arrow
🔝	TOP arrow	Symbols	arrow
🛐	place of worship	Symbols	religion
⚛️	atom symbol	Symbols	religion
🕉️	om	Symbols	religion
✡️	star of David	Symbols	religion
☸️	wheel of dharma	Symbols	religion
☯️	yin yang	Symbols	religion
✝️	latin cross	Symbols	religion
☦️	orthodox cross	Symbols	religion
☪️	star and crescent	Symbols	religion
☮️	peace symbol	Symbols	religion
🕎	menorah	Symbols	religion
🔯	dotted six-pointed star	Symbols	religion
🪯	khanda	Symbols	religion
♈	Aries	Symbols	zodiac
♉	Taurus	Symbols	zodiac
♊	Gemini	Symbols	zodiac
♋	Cancer	Symbols	zodiac
♌	Leo	Symbols	zodiac
♍	Virgo	Symbols	zodiac
♎	Libra	Symbols	zodiac
♏	Scorpio	Symbols	zodiac
♐	Sagittarius	Symbols	zodiac
♑	Capricorn	Symbols	zodiac
♒	Aquarius	Symbols	zodiac
♓	Pisces	Symbols	zodiac
⛎	Ophiuchus	Symbols	zodiac
🔀	shuffle tracks button	Symbols	av-symbol
🔁	repeat button	Symbols	av-symbol
🔂	repeat single button	Symbols	av-symbol
▶️	play button	Symbols	av-symbol
⏩	fast-forward button	Symbols	av-symbol
⏭️	next track button	Symbols	av-symbol
⏯️	play or pause button	Symbols	av-symbol
◀️	reverse button	Symbols	av-symbol
⏪	fast reverse button	Symbols	av-symbol
⏮️	last track button	Symbols	av-symbol
🔼	upwards button	Symbols	av-symbol
⏫	fast up button	Symbols	av-symbol
🔽	downwards button	Symbols	av-symbol
⏬	fast down button	Symbols	av-symbol
⏸️	pause button	Symbols	av-symbol
⏹️	stop button	Symbols	av-symbol
⏺️	record button	Symbols	av-symbol
⏏️	eject button	Symbols	av-symbol
🎦	cinema	Symbols	av-symbol
🔅	dim button	Symbols	av-symbol
🔆	bright button	Symbols	av-symbol
📶	antenna bars	Symbols	av-symbol
🛜	wireless	Symbols	av-symbol
📳	vibration mode	Symbols	av-symbol
📴	mobile phone off	Symbols	av-symbol
♀️	female sign	Symbols	gender
♂️	male sign	Symbols	gender
⚧️	transgender symbol	Symbols	gender
✖️	multiply	Symbols	math
➕	plus	Symbols	math
➖	minus	Symbols	math
➗	divide	Symbols	math
🟰	heavy equals sign	Symbols	math
♾️	infinity	Symbols	math
‼️	double exclamation mark	Symbols	punctuation
⁉️	exclamation question mark	Symbols	punctuation
❓	red question mark	Symbols	punctuation
❔	white question mark	Symbols	punctuation
❕	white exclamation mark	Symbols	punctuation
❗	red exclamation mark	Symbols	punctuation
〰️	wavy dash	Symbols	punctuation
💱	currency exchange	Symbols	currency
💲	heavy dollar sign	Symbols	currency
⚕️	medical symbol	Symbols	other-symbol
♻️	recycling symbol	Symbols	other-symbol
⚜️	fleur-de-lis	Symbols	other-symbol
🔱	trident emblem	Symbols	other-symbol
📛	name badge	Symbols	other-symbol
🔰	Japanese symbol for beginner	Symbols	other-symbol
⭕	hollow red circle	Symbols	other-symbol
✅	check mark button	Symbols	other-symbol
☑️	check box with check	Symbols	other-symbol
✔️	check mark	Symbols	other-symbol
❌	cross mark	Symbols	other-symbol
❎	cross mark button	Symbols	other-symbol
➰	curly loop	Symbols	other-symbol
➿	double curly loop	Symbols	other-symbol
〽️	part alternation mark	Symbols	other-symbol
✳️	eight-spoked asterisk	Symbols	other-symbol
✴️	eight-pointed star	Symbols	other-symbol
❇️	sparkle	Symbols	other-symbol
©️	copyright	Symbols	other-symbol
®️	registered	Symbols	other-symbol
™️	trade mark	Symbols	other-symbol
🫟	splatter	Symbols	other-symbol
#️⃣	keycap: #	Symbols	keycap
*️⃣	keycap: *	Symbols	keycap
0️⃣	keycap: 0	Symbols	keycap
1️⃣	keycap: 1	Symbols	keycap
2️⃣	keycap: 2	Symbols	keycap
3️⃣	keycap: 3	Symbols	keycap
4️⃣	keycap: 4	Symbols	keycap
5️⃣	keycap: 5	Symbols	keycap
6️⃣	keycap: 6	Symbols	keycap
7️⃣	keycap: 7	Symbols	keycap
8️⃣	keycap: 8	Symbols	keycap
9️⃣	keycap: 9	Symbols	keycap
🔟	keycap: 10	Symbols	keycap
🔠	input latin uppercase	Symbols	alphanum
🔡	input latin lowercase	Symbols	alphanum
🔢	input numbers	Symbols	alphanum
🔣	input symbols	Symbols	alphanum
🔤	input latin letters	Symbols	alphanum
🅰️	A button (blood type)	Symbols	alphanum
🆎	AB button (blood type)	Symbols	alphanum
🅱️	B button (blood type)	Symbols	alphanum
🆑	CL button	Symbols	alphanum
🆒	COOL button	Symbols	alphanum
🆓	FREE button	Symbols	alphanum
ℹ️	information	Symbols	alphanum
🆔	ID button	Symbols	alphanum
Ⓜ️	circled M	Symbols	alphanum
🆕	NEW button	Symbols	alphanum
🆖	NG button	Symbols	alphanum
🅾️	O button (blood type)	Symbols	alphanum
🆗	OK button	Symbols	alphanum
🅿️	P button	Symbols	alphanum
🆘	SOS button	Symbols	alphanum
🆙	UP! button	Symbols	alphanum
🆚	VS button	Symbols	alphanum
🈁	Japanese “here” button	Symbols	alphanum
🈂️	Japanese “service charge” button	Symbols	alphanum
🈷️	Japanese “monthly amount” button	Symbols	alphanum
🈶	Japanese “not free of charge” button	Symbols	alphanum
🈯	Japanese “reserved” button	Symbols	alphanum
🉐	Japanese “bargain” button	Symbols	alphanum
🈹	Japanese “discount” button	Symbols	alphanum
🈚	Japanese “free of charge” button	Symbols	alphanum
🈲	Japanese “prohibited” button	Symbols	alphanum
🉑	Japanese “acceptable” button	Symbols	alphanum
🈸	Japanese “application” button	Symbols	alphanum
🈴	Japanese “passing grade” button	Symbols	alphanum
🈳	Japanese “vacancy” button	Symbols	alphanum
㊗️	Japanese “congratulations” button	Symbols	alphanum
㊙️	Japanese “secret” button	Symbols	alphanum
🈺	Japanese “open for business” button	Symbols	alphanum
🈵	Japanese “no vacancy” button	Symbols	alphanum
🔴	red circle	Symbols	geometric
🟠	orange circle	Symbols	geometric
🟡	yellow circle	Symbols	geometric
🟢	green circle	Symbols	geometric
🔵	blue circle	Symbols	geometric
🟣	purple circle	Symbols	geometric
🟤	brown circle	Symbols	geometric
⚫	black circle	Symbols	geometric
⚪	white circle	Symbols	geometric
🟥	red square	Symbols	geometric
🟧	orange square	Symbols	geometric
🟨	yellow square	Symbols	geometric
🟩	green square	Symbols	geometric
🟦	blue square	Symbols	geometric
🟪	purple square	Symbols	geometric
🟫	brown square	Symbols	geometric
⬛	black large square	Symbols	geometric
⬜	white large square	Symbols	geometric
◼️	black medium square	Symbols	geometric
◻️	white medium square	Symbols	geometric
◾	black medium-small square	Symbols	geometric
◽	white medium-small square	Symbols	geometric
▪️	black small square	Symbols	geometric
▫️	white small square	Symbols	geometric
🔶	large orange diamond	Symbols	geometric
🔷	large blue diamond	Symbols	geometric
🔸	small orange diamond	Symbols	geometric
🔹	small blue diamond	Symbols	geometric
🔺	red triangle pointed up	Symbols	geometric
🔻	red triangle pointed down	Symbols	geometric
💠	diamond with a dot	Symbols	geometric
🔘	radio button	Symbols	geometric
🔳	white square button	Symbols	geometric
🔲	black square button	Symbols	geometric
🏁	chequered flag	Flags	flag
🚩	triangular flag	Flags	flag
🎌	crossed flags	Flags	flag
🏴	black flag	Flags	flag
🏳️	white flag	Flags	flag
🏳️‍🌈	rainbow flag	Flags	flag
🏳️‍⚧️	transgender flag	Flags	flag
🏴‍☠️	pirate flag	Flags	flag
🇦🇨	flag: Ascension Island	Flags	country-flag
🇦🇩	flag: Andorra	Flags	country-flag
🇦🇪	flag: United Arab Emirates	Flags	country-flag
🇦🇫	flag: Afghanistan	Flags	country-flag
🇦🇬	flag: Antigua & Barbuda	Flags	country-flag
🇦🇮	flag: Anguilla	Flags	country-flag
🇦🇱	flag: Albania	Flags	country-flag
🇦🇲	flag: Armenia	Flags	country-flag
🇦🇴	flag: Angola	Flags	country-flag
🇦🇶	flag: Antarctica	Flags	country-flag
🇦🇷	flag: Argentina	Flags	country-flag
🇦🇸	flag: American Samoa	Flags	country-flag
🇦🇹	flag: Austria	Flags	country-flag
🇦🇺	flag: Australia	Flags	country-flag
🇦🇼	flag: Aruba	Flags	country-flag
🇦🇽	flag: Åland Islands	Flags	country-flag
🇦🇿	flag: Azerbaijan	Flags	country-flag
🇧🇦	flag: Bosnia & Herzegovina	Flags	country-flag
🇧🇧	flag: Barbados	Flags	country-flag
🇧🇩	flag: Bangladesh	Flags	country-flag
🇧🇪	flag: Belgium	Flags	country-flag
🇧🇫	flag: Burkina Faso	Flags	country-flag
🇧🇬	flag: Bulgaria	Flags	country-flag
🇧🇭	flag: Bahrain	Flags	country-flag
🇧🇮	flag: Burundi	Flags	country-flag
🇧🇯	flag: Benin	Flags	country-flag
🇧🇱	flag: St. Barthélemy	Flags	country-flag
🇧🇲	flag: Bermuda	Flags	country-flag
🇧🇳	flag: Brunei	Flags	country-flag
🇧🇴	flag: Bolivia	Flags	country-flag
🇧🇶	flag: Caribbean Netherlands	Flags	country-flag
🇧🇷	flag: Brazil	Flags	country-flag
🇧🇸	flag: Bahamas	Flags	country-flag
🇧🇹	flag: Bhutan	Flags	country-flag
🇧🇻	flag: Bouvet Island	Flags	country-flag
🇧🇼	flag: Botswana	Flags	country-flag
🇧🇾	flag: Belarus	Flags	country-flag
🇧🇿	flag: Belize	Flags	country-flag
🇨🇦	flag: Canada	Flags	country-flag
🇨🇨	flag: Cocos (Keeling) Islands	Flags	country-flag
🇨🇩	flag: Congo - Kinshasa	Flags	country-flag
🇨🇫	flag: Central African Republic	Flags	country-flag
🇨🇬	flag: Congo - Brazzaville	Flags	country-flag
🇨🇭	flag: Switzerland	Flags	country-flag
🇨🇮	flag: Côte d’Ivoire	Flags	country-flag
🇨🇰	flag: Cook Islands	Flags	country-flag
🇨🇱	flag: Chile	Flags	country-flag
🇨🇲	flag: Cameroon	Flags	country-flag
🇨🇳	flag: China	Flags	country-flag
🇨🇴	flag: Colombia	Flags	country-flag
🇨🇵	flag: Clipperton Island	Flags	country-flag
🇨🇶	flag: Sark	Flags	country-flag
🇨🇷	flag: Costa Rica	Flags	country-flag
🇨🇺	flag: Cuba	Flags	country-flag
🇨🇻	flag: Cape Verde	Flags	country-flag
🇨🇼	flag: Curaçao	Flags	country-flag
🇨🇽	flag: Christmas Island	Flags	country-flag
🇨🇾	flag: Cyprus	Flags	country-flag
🇨🇿	flag: Czechia	Flags	country-flag
🇩🇪	flag: Germany	Flags	country-flag
🇩🇬	flag: Diego Garcia	Flags	country-flag
🇩🇯	flag: Djibouti	Flags	country-flag
🇩🇰	flag: Denmark	Flags	country-flag
🇩🇲	flag: Dominica	Flags	country-flag
🇩🇴	flag: Dominican Republic	Flags	country-flag
🇩🇿	flag: Algeria	Flags	country-flag
🇪🇦	flag: Ceuta & Melilla	Flags	country-flag
🇪🇨	flag: Ecuador	Flags	country-flag
🇪🇪	flag: Estonia	Flags	country-flag
🇪🇬	flag: Egypt	Flags	country-flag
🇪🇭	flag: Western Sahara	Flags	country-flag
🇪🇷	flag: Eritrea	Flags	country-flag
🇪🇸	flag: Spain	Flags	country-flag
🇪🇹	flag: Ethiopia	Flags	country-flag
🇪🇺	flag: European Union	Flags	country-flag
🇫🇮	flag: Finland	Flags	country-flag
🇫🇯	flag: Fiji	Flags	country-flag
🇫🇰	flag: Falkland Islands	Flags	country-flag
🇫🇲	flag: Micronesia	Flags	country-flag
🇫🇴	flag: Faroe Islands	Flags	country-flag
🇫🇷	flag: France	Flags	country-flag
🇬🇦	flag: Gabon	Flags	country-flag
🇬🇧	flag: United Kingdom	Flags	country-flag
🇬🇩	flag: Grenada	Flags	country-flag
🇬🇪	flag: Georgia	Flags	country-flag
🇬🇫	flag: French Guiana	Flags	country-flag
🇬🇬	flag: Guernsey	Flags	country-flag
🇬🇭	flag: Ghana	Flags	country-flag
🇬🇮	flag: Gibraltar	Flags	country-flag
🇬🇱	flag: Greenland	Flags	country-flag
🇬🇲	flag: Gambia	Flags	country-flag
🇬🇳	flag: Guinea	Flags	country-flag
🇬🇵	flag: Guadeloupe	Flags	country-flag
🇬🇶	flag: Equatorial Guinea	Flags	country-flag
🇬🇷	flag: Greece	Flags	country-flag
🇬🇸	flag: South Georgia & South Sandwich Islands	Flags	country-flag
🇬🇹	flag: Guatemala	Flags	country-flag
🇬🇺	flag: Guam	Flags	country-flag
🇬🇼	flag: Guinea-Bissau	Flags	country-flag
🇬🇾	flag: Guyana	Flags	country-flag
🇭🇰	flag: Hong Kong SAR China	Flags	country-flag
🇭🇲	flag: Heard & McDonald Islands	Flags	country-flag
🇭🇳	flag: Honduras	Flags	country-flag
🇭🇷	flag: Croatia	Flags	country-flag
🇭🇹	flag: Haiti	Flags	country-flag
🇭🇺	flag: Hungary	Flags	country-flag
🇮🇨	flag: Canary Islands	Flags	country-flag
🇮🇩	flag: Indonesia	Flags	country-flag
🇮🇪	flag: Ireland	Flags	country-flag
🇮🇱	flag: Israel	Flags	country-flag
🇮🇲	flag: Isle of Man	Flags	country-flag
🇮🇳	flag: India	Flags	country-flag
🇮🇴	flag: British Indian Ocean Territory	Flags	country-flag
🇮🇶	flag: Iraq	Flags	country-flag
🇮🇷	flag: Iran	Flags	country-flag
🇮🇸	flag: Iceland	Flags	country-flag
🇮🇹	flag: Italy	Flags	country-flag
🇯🇪	flag: Jersey	Flags	country-flag
🇯🇲	flag: Jamaica	Flags	country-flag
🇯🇴	flag: Jordan	Flags	country-flag
🇯🇵	flag: Japan	Flags	country-flag
🇰🇪	flag: Kenya	Flags	country-flag
🇰🇬	flag: Kyrgyzstan	Flags	country-flag
🇰🇭	flag: Cambodia	Flags	country-flag
🇰🇮	flag: Kiribati	Flags	country-flag
🇰🇲	flag: Comoros	Flags	country-flag
🇰🇳	flag: St. Kitts & Nevis	Flags	country-flag
🇰🇵	flag: North Korea	Flags	country-flag
🇰🇷	flag: South Korea	Flags	country-flag
🇰🇼	flag: Kuwait	Flags	country-flag
🇰🇾	flag: Cayman Islands	Flags	country-flag
🇰🇿	flag: Kazakhstan	Flags	country-flag
🇱🇦	flag: Laos	Flags	country-flag
🇱🇧	flag: Lebanon	Flags	country-flag
🇱🇨	flag: St. Lucia	Flags	country-flag
🇱🇮	flag: Liechtenstein	Flags	country-flag
🇱🇰	flag: Sri Lanka	Flags	country-flag
🇱🇷	flag: Liberia	Flags	country-flag
🇱🇸	flag: Lesotho	Flags	country-flag
🇱🇹	flag: Lithuania	Flags	country-flag
🇱🇺	flag: Luxembourg	Flags	country-flag
🇱🇻	flag: Latvia	Flags	country-flag
🇱🇾	flag: Libya	Flags	country-flag
🇲🇦	flag: Morocco	Flags	country-flag
🇲🇨	flag: Monaco	Flags	country-flag
🇲🇩	flag: Moldova	Flags	country-flag
🇲🇪	flag: Montenegro	Flags	country-flag
🇲🇫	flag: St. Martin	Flags	country-flag
🇲🇬	flag: Madagascar	Flags	country-flag
🇲🇭	flag: Marshall Islands	Flags	country-flag
🇲🇰	flag: North Macedonia	Flags	country-flag
🇲🇱	flag: Mali	Flags	country-flag
🇲🇲	flag: Myanmar (Burma)	Flags	country-flag
🇲🇳	flag: Mongolia	Flags	country-flag
🇲🇴	flag: Macao SAR China	Flags	country-flag
🇲🇵	flag: Northern Mariana Islands	Flags	country-flag
🇲🇶	flag: Martinique	Flags	country-flag
🇲🇷	flag: Mauritania	Flags	country-flag
🇲🇸	flag: Montserrat	Flags	country-flag
🇲🇹	flag: Malta	Flags	country-flag
🇲🇺	flag: Mauritius	Flags	country-flag
🇲🇻	flag: Maldives	Flags	country-flag
🇲🇼	flag: Malawi	Flags	country-flag
🇲🇽	flag: Mexico	Flags	country-flag
🇲🇾	flag: Malaysia	Flags	country-flag
🇲🇿	flag: Mozambique	Flags	country-flag
🇳🇦	flag: Namibia	Flags	country-flag
🇳🇨	flag: New Caledonia	Flags	country-flag
🇳🇪	flag: Niger	Flags	country-flag
🇳🇫	flag: Norfolk Island	Flags	country-flag
🇳🇬	flag: Nigeria	Flags	country-flag
🇳🇮	flag: Nicaragua	Flags	country-flag
🇳🇱	flag: Netherlands	Flags	country-flag
🇳🇴	flag: Norway	Flags	country-flag
🇳🇵	flag: Nepal	Flags	country-flag
🇳🇷	flag: Nauru	Flags	country-flag
🇳🇺	flag: Niue	Flags	country-flag
🇳🇿	flag: New Zealand	Flags	country-flag
🇴🇲	flag: Oman	Flags	country-flag
🇵🇦	flag: Panama	Flags	country-flag
🇵🇪	flag: Peru	Flags	country-flag
🇵🇫	flag: French Polynesia	Flags	country-flag
🇵🇬	flag: Papua New Guinea	Flags	country-flag
🇵🇭	flag: Philippines	Flags	country-flag
🇵🇰	flag: Pakistan	Flags	country-flag
🇵🇱	flag: Poland	Flags	country-flag
🇵🇲	flag: St. Pierre & Miquelon	Flags	country-flag
🇵🇳	flag: Pitcairn Islands	Flags	country-flag
🇵🇷	flag: Puerto Rico	Flags	country-flag
🇵🇸	flag: Palestinian Territories	Flags	country-flag
🇵🇹	flag: Portugal	Flags	country-flag
🇵🇼	flag: Palau	Flags	country-flag
🇵🇾	flag: Paraguay	Flags	country-flag
🇶🇦	flag: Qatar	Flags	country-flag
🇷🇪	flag: Réunion	Flags	country-flag
🇷🇴	flag: Romania	Flags	country-flag
🇷🇸	flag: Serbia	Flags	country-flag
🇷🇺	flag: Russia	Flags	country-flag
🇷🇼	flag: Rwanda	Flags	country-flag
🇸🇦	flag: Saudi Arabia	Flags	country-flag
🇸🇧	flag: Solomon Islands	Flags	country-flag
🇸🇨	flag: Seychelles	Flags	country-flag
🇸🇩	flag: Sudan	Flags	country-flag
🇸🇪	flag: Sweden	Flags	country-flag
🇸🇬	flag: Singapore	Flags	country-flag
🇸🇭	flag: St. Helena	Flags	country-flag
🇸🇮	flag: Slovenia	Flags	country-flag
🇸🇯	flag: Svalbard & Jan Mayen	Flags	country-flag
🇸🇰	flag: Slovakia	Flags	country-flag
🇸🇱	flag: Sierra Leone	Flags	country-flag
🇸🇲	flag: San Marino	Flags	country-flag
🇸🇳	flag: Senegal	Flags	country-flag
🇸🇴	flag: Somalia	Flags	country-flag
🇸🇷	flag: Suriname	Flags	country-flag
🇸🇸	flag: South Sudan	Flags	country-flag
🇸🇹	flag: São Tomé & Príncipe	Flags	country-flag
🇸🇻	flag: El Salvador	Flags	country-flag
🇸🇽	flag: Sint Maarten	Flags	country-flag
🇸🇾	flag: Syria	Flags	country-flag
🇸🇿	flag: Eswatini	Flags	country-flag
🇹🇦	flag: Tristan da Cunha	Flags	country-flag
🇹🇨	flag: Turks & Caicos Islands	Flags	country-flag
🇹🇩	flag: Chad	Flags	country-flag
🇹🇫	flag: French Southern Territories	Flags	country-flag
🇹🇬	flag: Togo	Flags	country-flag
🇹🇭	flag: Thailand	Flags	country-flag
🇹🇯	flag: Tajikistan	Flags	country-flag
🇹🇰	flag: Tokelau	Flags	country-flag
🇹🇱	flag: Timor-Leste	Flags	country-flag
🇹🇲	flag: Turkmenistan	Flags	country-flag
🇹🇳	flag: Tunisia	Flags	country-flag
🇹🇴	flag: Tonga	Flags	country-flag
🇹🇷	flag: Türkiye	Flags	country-flag
🇹🇹	flag: Trinidad & Tobago	Flags	country-flag
🇹🇻	flag: Tuvalu	Flags	country-flag
🇹🇼	flag: Taiwan	Flags	country-flag
🇹🇿	flag: Tanzania	Flags	country-flag
🇺🇦	flag: Ukraine	Flags	country-flag
🇺🇬	flag: Uganda	Flags	country-flag
🇺🇲	flag: U.S. Outlying Islands	Flags	country-flag
🇺🇳	flag: United Nations	Flags	country-flag
🇺🇸	flag: United States	Flags	country-flag
🇺🇾	flag: Uruguay	Flags	country-flag
🇺🇿	flag: Uzbekistan	Flags	country-flag
🇻🇦	flag: Vatican City	Flags	country-flag
🇻🇨	flag: St. Vincent & Grenadines	Flags	country-flag
🇻🇪	flag: Venezuela	Flags	country-flag
🇻🇬	flag: British Virgin Islands	Flags	country-flag
🇻🇮	flag: U.S. Virgin Islands	Flags	country-flag
🇻🇳	flag: Vietnam	Flags	country-flag
🇻🇺	flag: Vanuatu	Flags	country-flag
🇼🇫	flag: Wallis & Futuna	Flags	country-flag
🇼🇸	flag: Samoa	Flags	country-flag
🇽🇰	flag: Kosovo	Flags	country-flag
🇾🇪	flag: Yemen	Flags	country-flag
🇾🇹	flag: Mayotte	Flags	country-flag
🇿🇦	flag: South Africa	Flags	country-flag
🇿🇲	flag: Zambia	Flags	country-flag
🇿🇼	flag: Zimbabwe	Flags	country-flag
🏴󠁧󠁢󠁥󠁮󠁧󠁿	flag: England	Flags	subdivision-flag
🏴󠁧󠁢󠁳󠁣󠁴󠁿	flag: Scotland	Flags	subdivision-flag
🏴󠁧󠁢󠁷󠁬󠁳󠁿	flag: Wales	Flags	subdivision-flag
"""

private val EMOJI_DATA_CHUNKS = listOf(
    EMOJI_DATA_0,
    EMOJI_DATA_1,
    EMOJI_DATA_2,
    EMOJI_DATA_3,
    EMOJI_DATA_4,
)

internal val ALL_EMOJI_OPTIONS: List<EmojiOption> by lazy {
    EMOJI_DATA_CHUNKS.flatMap { chunk ->
        chunk.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("\t")
                if (parts.size == 4) EmojiOption(parts[0], parts[1], parts[2], parts[3]) else null
            }
            .toList()
    }
}
