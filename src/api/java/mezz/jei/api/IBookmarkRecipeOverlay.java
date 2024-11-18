package mezz.jei.api;

import mezz.jei.api.gui.IRecipeLayout;

import javax.annotation.Nullable;

/**
 * The {@link IBookmarkRecipeOverlay} is JEI's gui that displays all the bookmarked ingredients next to an open container gui.
 * Use this interface to get information from it.
 * Get the instance from {@link IJeiRuntime#getBookmarkOverlay()}.
 *
 * @since JEI 4.15.0
 */
public interface IBookmarkRecipeOverlay {
	/**
	 * @return the ingredient that's currently under the mouse, or null if there is none.
	 */
	@Nullable
	boolean drop(IRecipeLayout recipeLayout, int posX, int posY);
}
