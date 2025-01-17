package mezz.jei.gui.elements;

import net.minecraft.client.Minecraft;

import mezz.jei.api.gui.IDrawableAnimated;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.gui.TickTimer;

public class DrawableAnimated implements IDrawableAnimated {
	private final IDrawableStatic drawable;
	private final ITickTimer tickTimer;
	private final StartDirection startDirection;

	public DrawableAnimated(IDrawableStatic drawable, int ticksPerCycle, IDrawableAnimated.StartDirection startDirection, boolean inverted) {
		StartDirection animationStartDirection = getStartDirection(startDirection, inverted);

		int tickTimerMaxValue;
		if (animationStartDirection == IDrawableAnimated.StartDirection.TOP || animationStartDirection == IDrawableAnimated.StartDirection.BOTTOM) {
			tickTimerMaxValue = drawable.getHeight();
		} else {
			tickTimerMaxValue = drawable.getWidth();
		}
		this.drawable = drawable;
		this.tickTimer = new TickTimer(ticksPerCycle, tickTimerMaxValue, !inverted);
		this.startDirection = animationStartDirection;
	}

	private static StartDirection getStartDirection(StartDirection startDirection, boolean inverted) {
		StartDirection animationStartDirection = startDirection;
		if (inverted) {
			if (startDirection == StartDirection.TOP) {
				animationStartDirection = StartDirection.BOTTOM;
			} else if (startDirection == StartDirection.BOTTOM) {
				animationStartDirection = StartDirection.TOP;
			} else if (startDirection == StartDirection.LEFT) {
				animationStartDirection = StartDirection.RIGHT;
			} else {
				animationStartDirection = StartDirection.LEFT;
			}
		}
		return animationStartDirection;
	}

	public DrawableAnimated(IDrawableStatic drawable, ITickTimer tickTimer, StartDirection startDirection) {
		this.drawable = drawable;
		this.tickTimer = tickTimer;
		this.startDirection = startDirection;
	}

	@Override
	public int getWidth() {
		return drawable.getWidth();
	}

	@Override
	public int getHeight() {
		return drawable.getHeight();
	}

	@Override
	public void draw(Minecraft minecraft, int xOffset, int yOffset) {
		int maskLeft = 0;
		int maskRight = 0;
		int maskTop = 0;
		int maskBottom = 0;

		int animationValue = tickTimer.getValue();

		switch (startDirection) {
			case TOP:
				maskBottom = animationValue;
				break;
			case BOTTOM:
				maskTop = animationValue;
				break;
			case LEFT:
				maskRight = animationValue;
				break;
			case RIGHT:
				maskLeft = animationValue;
				break;
			default:
				throw new IllegalStateException("Unknown startDirection " + startDirection);
		}

		drawable.draw(minecraft, xOffset, yOffset, maskTop, maskBottom, maskLeft, maskRight);
	}
}
