package rs117.hdequiptest.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.AnimationID;

/**
 * What the character does in the equipment preview.
 */
@Getter
@RequiredArgsConstructor
public enum EquipmentPreviewPose {
	LIVE("As in game", Kind.LIVE, -1),
	STAND("Stand", Kind.STAND, -1),
	WALK("Walk", Kind.WALK, -1),
	RUN("Run", Kind.RUN, -1),
	WAVE("Wave", Kind.EMOTE, AnimationID.EMOTE_WAVE),
	YES("Yes", Kind.EMOTE, AnimationID.EMOTE_YES),
	NO("No", Kind.EMOTE, AnimationID.EMOTE_NO),
	BOW("Bow", Kind.EMOTE, AnimationID.EMOTE_BOW),
	THINK("Think", Kind.EMOTE, AnimationID.EMOTE_THINK),
	ANGRY("Angry", Kind.EMOTE, AnimationID.EMOTE_ANGRY),
	CRY("Cry", Kind.EMOTE, AnimationID.EMOTE_CRY),
	LAUGH("Laugh", Kind.EMOTE, AnimationID.EMOTE_LAUGH),
	CHEER("Cheer", Kind.EMOTE, AnimationID.EMOTE_CHEER),
	BECKON("Beckon", Kind.EMOTE, AnimationID.EMOTE_BECKON),
	CLAP("Clap", Kind.EMOTE, AnimationID.EMOTE_CLAP),
	SHRUG("Shrug", Kind.EMOTE, AnimationID.EMOTE_SHRUG),
	SALUTE("Salute", Kind.EMOTE, AnimationID.EMOTE_FREMMENIK_SALUTE),
	FLEX("Flex", Kind.EMOTE, AnimationID.EMOTE_FLEX),
	BLOW_KISS("Blow kiss", Kind.EMOTE, AnimationID.EMOTE_BLOW_KISS),
	YAWN("Yawn", Kind.EMOTE, AnimationID.EMOTE_YAWN),
	PANIC("Panic", Kind.EMOTE, AnimationID.EMOTE_PANIC),
	RASPBERRY("Raspberry", Kind.EMOTE, AnimationID.EMOTE_YA_BOO_SUCKS),
	JUMP_FOR_JOY("Jump for joy", Kind.EMOTE, AnimationID.EMOTE_JUMP_WITH_JOY),
	DANCE("Dance", Kind.EMOTE, AnimationID.EMOTE_DANCE),
	JIG("Jig", Kind.EMOTE, AnimationID.EMOTE_DANCE_SCOTTISH),
	SPIN("Spin", Kind.EMOTE, AnimationID.EMOTE_DANCE_SPIN),
	HEADBANG("Headbang", Kind.EMOTE, AnimationID.EMOTE_DANCE_HEADBANG),
	AIR_GUITAR("Air guitar", Kind.EMOTE, AnimationID.EMOTE_AIR_GUITAR);

	public enum Kind {
		/** The player's own in-world animation, untouched */
		LIVE,
		/** The player's idle stance, which depends on the weapon */
		STAND,
		WALK,
		RUN,
		/** A fixed animation */
		EMOTE
	}

	private final String displayName;
	private final Kind kind;
	/** The animation for {@link Kind#EMOTE}, or -1 */
	private final int emoteAnimation;

	@Override
	public String toString() {
		return displayName;
	}
}
