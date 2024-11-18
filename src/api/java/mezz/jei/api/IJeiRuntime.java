package mezz.jei.api;

import com.google.gson.Gson;

/**
 * Gives access to JEI functions that are available once everything has loaded.
 * The IJeiRuntime instance is passed to your mod plugin in {@link IModPlugin#onRuntimeAvailable(IJeiRuntime)}.
 */
public interface IJeiRuntime {
	IRecipeRegistry getRecipeRegistry();

	/**
	 * @since JEI 3.2.12
	 */
	IRecipesGui getRecipesGui();

	/**
	 * @since JEI 4.2.2
	 */
	IIngredientFilter getIngredientFilter();

	/**
	 * @since JEI 4.2.2
	 */
	IIngredientListOverlay getIngredientListOverlay();

	/**
	 * @since JEI 4.15.0
	 */
	IBookmarkOverlay getBookmarkOverlay();

	/**
	 * @since YEI
	 */
	IBookmarkRecipeOverlay getBookmarkRecipeOverlay();

	/**
	 * @since YEI
	 */
	Gson getGson();

	/**
	 * @deprecated since JEI 4.5.0. Use {@link #getIngredientListOverlay()}
	 */
	@Deprecated
	IItemListOverlay getItemListOverlay();
}
