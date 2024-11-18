package mezz.jei.gui.recipes;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import mezz.jei.runtime.JeiRuntime;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

import mezz.jei.Internal;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.Focus;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.ingredients.GuiFluidStackGroup;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import mezz.jei.gui.ingredients.GuiItemStackGroup;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import org.lwjgl.input.Mouse;

public class RecipeLayout implements IRecipeLayoutDrawable {
	public static final int RECIPE_BUTTON_SIZE = 13;
	public static final int RECIPE_BORDER_PADDING = 4;
	public static final int recipeTransferButtonIndex = 100;
	public static final int recipeBookmarkButtonIndex = 200;

	private final int ingredientCycleOffset = (int) ((Math.random() * 10000) % Integer.MAX_VALUE);
	private final IRecipeCategory recipeCategory;
	private final GuiItemStackGroup guiItemStackGroup;
	private final GuiFluidStackGroup guiFluidStackGroup;
	private final Map<IIngredientType, GuiIngredientGroup> guiIngredientGroups;
	@Nullable
	private final RecipeTransferButton recipeTransferButton;
	@Nullable
	private final RecipeBookmarkButton recipeBookmarkButton;
	private final IRecipeWrapper recipeWrapper;
	@Nullable
	private final IFocus<?> focus;
	private final Color highlightColor = new Color(0x7FFFFFFF, true);
	@Nullable
	private ShapelessIcon shapelessIcon;
	private final DrawableNineSliceTexture recipeBorder;

	private int posX;
	private int posY;
	private final int initPosX;
	private final int initPosY;
	private boolean isDragging = false;
	private int lastMouseX;
	private int lastMouseY;
	private final int backgroundWidth;
	private final int backgroundHeight;
	private Minecraft mc;

	@Nullable
	public static <T extends IRecipeWrapper> RecipeLayout create(int index, IRecipeCategory<T> recipeCategory, T recipeWrapper, @Nullable IFocus focus, int posX, int posY) {
		RecipeLayout recipeLayout = new RecipeLayout(index, recipeCategory, recipeWrapper, focus, posX, posY);
		try {
			IIngredients ingredients = new Ingredients();
			recipeWrapper.getIngredients(ingredients);
			recipeCategory.setRecipe(recipeLayout, recipeWrapper, ingredients);
			return recipeLayout;
		} catch (RuntimeException | LinkageError e) {
			Log.get().error("Error caught from Recipe Category: {}", recipeCategory.getClass().getCanonicalName(), e);
		}
		return null;
	}

	private <T extends IRecipeWrapper> RecipeLayout(int index, IRecipeCategory<T> recipeCategory, T recipeWrapper, @Nullable IFocus<?> focus, int posX, int posY) {
		this.mc = Minecraft.getMinecraft();
		this.backgroundWidth = recipeCategory.getBackground().getWidth();
		this.backgroundHeight = recipeCategory.getBackground().getHeight();
		ErrorUtil.checkNotNull(recipeCategory, "recipeCategory");
		ErrorUtil.checkNotNull(recipeWrapper, "recipeWrapper");
		if (focus != null) {
			focus = Focus.check(focus);
		}
		this.recipeCategory = recipeCategory;
		this.focus = focus;

		IFocus<ItemStack> itemStackFocus = null;
		IFocus<FluidStack> fluidStackFocus = null;
		if (focus != null) {
			Object focusValue = focus.getValue();
			if (focusValue instanceof ItemStack) {
				//noinspection unchecked
				itemStackFocus = (IFocus<ItemStack>) focus;
			} else if (focusValue instanceof FluidStack) {
				//noinspection unchecked
				fluidStackFocus = (IFocus<FluidStack>) focus;
			}
		}
		this.guiItemStackGroup = new GuiItemStackGroup(itemStackFocus, ingredientCycleOffset);
		this.guiFluidStackGroup = new GuiFluidStackGroup(fluidStackFocus, ingredientCycleOffset);

		this.guiIngredientGroups = new Reference2ObjectArrayMap<>();
		this.guiIngredientGroups.put(VanillaTypes.ITEM, this.guiItemStackGroup);
		this.guiIngredientGroups.put(VanillaTypes.FLUID, this.guiFluidStackGroup);

		if (index >= 0) {
			IDrawable icon = Internal.getHelpers().getGuiHelper().getRecipeTransfer();
			this.recipeTransferButton = new RecipeTransferButton(recipeTransferButtonIndex + index, 0, 0, RECIPE_BUTTON_SIZE, RECIPE_BUTTON_SIZE, icon, this);
			IDrawable icon2 = Internal.getHelpers().getGuiHelper().getRecipeBookmark();
			this.recipeBookmarkButton = new RecipeBookmarkButton(recipeBookmarkButtonIndex + index, 0, 0, RECIPE_BUTTON_SIZE, RECIPE_BUTTON_SIZE, icon2, this);
		} else {
			this.recipeTransferButton = null;
			this.recipeBookmarkButton = null;
		}

		initPosX = posX;
		initPosY = posY;

		setPosition(posX, posY);

		this.recipeWrapper = recipeWrapper;
		this.recipeBorder = Internal.getHelpers().getGuiHelper().getRecipeBackground();
	}

	@Override
	public void setPosition(int posX, int posY) {
		this.posX = posX;
		this.posY = posY;

		if (this.recipeTransferButton != null) {
			int width = recipeCategory.getBackground().getWidth();
			int height = recipeCategory.getBackground().getHeight();
			this.recipeTransferButton.x = posX + width + RECIPE_BORDER_PADDING + 2;
			this.recipeTransferButton.y = posY + height - RECIPE_BUTTON_SIZE;
		}
		if (this.recipeBookmarkButton != null) {
			int width = recipeCategory.getBackground().getWidth();
			int height = recipeCategory.getBackground().getHeight();
			this.recipeBookmarkButton.x = posX + width + RECIPE_BORDER_PADDING + 2;
			this.recipeBookmarkButton.y = posY + height - RECIPE_BUTTON_SIZE * 2 - RECIPE_BORDER_PADDING;
		}
	}

	@Override
	@Deprecated
	public void draw(Minecraft minecraft, final int mouseX, final int mouseY) {
		drawRecipe(minecraft, mouseX, mouseY);
		drawOverlays(minecraft, mouseX, mouseY);
	}

	@Override
	public void drawRecipe(Minecraft minecraft, int mouseX, int mouseY) {
		IDrawable background = recipeCategory.getBackground();

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableLighting();
		GlStateManager.enableAlpha();

		final int recipeMouseX = mouseX - posX;
		final int recipeMouseY = mouseY - posY;

		GlStateManager.pushMatrix();
		GlStateManager.translate(posX, posY, 0.0F);
		{
			int adjustedWidth = backgroundWidth + (2 * RECIPE_BORDER_PADDING);
			int adjustedHeight = backgroundHeight + (2 * RECIPE_BORDER_PADDING);
			recipeBorder.draw(minecraft, -RECIPE_BORDER_PADDING, -RECIPE_BORDER_PADDING, adjustedWidth, adjustedHeight);
			background.draw(minecraft);
			recipeCategory.drawExtras(minecraft);
			recipeWrapper.drawInfo(minecraft, backgroundWidth, backgroundHeight, mouseX - posX, mouseY - posY);
			// drawExtras and drawInfo often render text which messes with the color, this clears it
			GlStateManager.color(1, 1, 1, 1);
			if (shapelessIcon != null) {
				shapelessIcon.draw(minecraft, background.getWidth());
			}
		}
		GlStateManager.popMatrix();

		for (GuiIngredientGroup guiIngredientGroup : guiIngredientGroups.values()) {
			guiIngredientGroup.draw(minecraft, posX, posY, highlightColor, mouseX, mouseY);
		}
		if (recipeTransferButton != null) {
			float partialTicks = minecraft.getRenderPartialTicks();
			recipeTransferButton.drawButton(minecraft, mouseX, mouseY, partialTicks);
		}
		if (recipeBookmarkButton != null) {
			float partialTicks = minecraft.getRenderPartialTicks();
			recipeBookmarkButton.drawButton(minecraft, mouseX, mouseY, partialTicks);
		}
		GlStateManager.disableBlend();
		GlStateManager.disableLighting();
		GlStateManager.disableAlpha();
	}

	@Override
	public void drawOverlays(Minecraft minecraft, int mouseX, int mouseY) {
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableLighting();
		GlStateManager.enableAlpha();

		final int recipeMouseX = mouseX - posX;
		final int recipeMouseY = mouseY - posY;

		GuiIngredient hoveredIngredient = null;
		for (GuiIngredientGroup guiIngredientGroup : guiIngredientGroups.values()) {
			hoveredIngredient = guiIngredientGroup.getHoveredIngredient(posX, posY, mouseX, mouseY);
			if (hoveredIngredient != null) {
				break;
			}
		}
		if (recipeTransferButton != null) {
			recipeTransferButton.drawToolTip(minecraft, mouseX, mouseY);
		}
		if (recipeBookmarkButton != null) {
			recipeBookmarkButton.drawToolTip(minecraft, mouseX, mouseY);
		}
		GlStateManager.disableBlend();
		GlStateManager.disableLighting();

		if (hoveredIngredient != null) {
			hoveredIngredient.drawOverlays(minecraft, posX, posY, recipeMouseX, recipeMouseY);
		} else if (isMouseOver(mouseX, mouseY)) {
			List<String> categoryTooltipStrings = LegacyUtil.getTooltipStrings(recipeCategory, recipeMouseX, recipeMouseY);
			List<String> tooltipStrings = new ArrayList<>(categoryTooltipStrings);
			List<String> wrapperTooltips = recipeWrapper.getTooltipStrings(recipeMouseX, recipeMouseY);
			//noinspection ConstantConditions
			if (wrapperTooltips != null) {
				tooltipStrings.addAll(wrapperTooltips);
			}
			if (tooltipStrings.isEmpty() && shapelessIcon != null) {
				tooltipStrings = shapelessIcon.getTooltipStrings(recipeMouseX, recipeMouseY);
			}
			if (tooltipStrings != null && !tooltipStrings.isEmpty()) {
				TooltipRenderer.drawHoveringText(minecraft, tooltipStrings, mouseX, mouseY);
			}
		}

		GlStateManager.disableAlpha();
	}

	@Override
	public boolean isMouseOver(int mouseX, int mouseY) {
		final IDrawable background = recipeCategory.getBackground();
		final Rectangle backgroundRect = new Rectangle(posX, posY, background.getWidth(), background.getHeight());
		return backgroundRect.contains(mouseX, mouseY) ||
			(recipeTransferButton != null && recipeTransferButton.isMouseOver()) || (recipeBookmarkButton != null && recipeBookmarkButton.isMouseOver());
	}

	@Override
	@Nullable
	public Object getIngredientUnderMouse(int mouseX, int mouseY) {
		GuiIngredient<?> guiIngredient = getGuiIngredientUnderMouse(mouseX, mouseY);
		if (guiIngredient != null) {
			return guiIngredient.getDisplayedIngredient();
		}

		return null;
	}

	@Nullable
	public GuiIngredient<?> getGuiIngredientUnderMouse(int mouseX, int mouseY) {
		for (GuiIngredientGroup<?> guiIngredientGroup : guiIngredientGroups.values()) {
			GuiIngredient<?> clicked = guiIngredientGroup.getHoveredIngredient(posX, posY, mouseX, mouseY);
			if (clicked != null) {
				return clicked;
			}
		}
		return null;
	}

	public void handleMouseInput() {
        assert this.mc.currentScreen != null;
        int mouseX = Mouse.getEventX() * this.mc.currentScreen.width / this.mc.displayWidth;
		int mouseY = this.mc.currentScreen.height - Mouse.getEventY() * this.mc.currentScreen.height / this.mc.displayHeight - 1;
		JeiRuntime runtime = Internal.getRuntime();

		if (Mouse.isButtonDown(0)) { // 鼠标左键拖动
			if (isDragging) {
				int deltaX = mouseX - lastMouseX;
				int deltaY = mouseY - lastMouseY;
				setPosition(posX + deltaX, posY + deltaY);
				lastMouseX = mouseX;
				lastMouseY = mouseY;
			}
		} else if (isDragging) {
			isDragging = false;
			runtime.getBookmarkRecipeOverlay().drop(this, lastMouseX, lastMouseY);
			setPosition(initPosX, initPosY);

		}


	}


	public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
		if (isMouseOver(mouseX, mouseY)) {
			lastMouseX = mouseX;
			lastMouseY = mouseY;

			if (mouseButton == 0) { // 左键拖动
				isDragging = true;
			}
		}
		return recipeWrapper.handleClick(minecraft, mouseX - posX, mouseY - posY, mouseButton);
	}

	@Override
	public GuiItemStackGroup getItemStacks() {
		return guiItemStackGroup;
	}

	@Override
	public IGuiFluidStackGroup getFluidStacks() {
		return guiFluidStackGroup;
	}

	public IRecipeWrapper getRecipeWrapper() {
		return recipeWrapper;
	}

	@Override
	public <T> IGuiIngredientGroup<T> getIngredientsGroup(IIngredientType<T> ingredientType) {
		@SuppressWarnings("unchecked")
		GuiIngredientGroup<T> guiIngredientGroup = guiIngredientGroups.get(ingredientType);
		if (guiIngredientGroup == null) {
			IFocus<T> focus = null;
			if (this.focus != null) {
				Object focusValue = this.focus.getValue();
				if (ingredientType.getIngredientClass().isInstance(focusValue)) {
					//noinspection unchecked
					focus = (IFocus<T>) this.focus;
				}
			}
			guiIngredientGroup = new GuiIngredientGroup<>(ingredientType, focus, ingredientCycleOffset);
			guiIngredientGroups.put(ingredientType, guiIngredientGroup);
		}
		return guiIngredientGroup;
	}

	@Override
	@Deprecated
	public <T> IGuiIngredientGroup<T> getIngredientsGroup(Class<T> ingredientClass) {
		IIngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		IIngredientType<T> ingredientType = ingredientRegistry.getIngredientType(ingredientClass);
		return getIngredientsGroup(ingredientType);
	}

	@Override
	public void setRecipeTransferButton(int posX, int posY) {
		if (recipeTransferButton != null) {
			recipeTransferButton.x = posX + this.posX;
			recipeTransferButton.y = posY + this.posY;
		}
		if (recipeBookmarkButton != null) {
			recipeBookmarkButton.x = posX + this.posX;
			recipeBookmarkButton.y = posY + this.posY - RECIPE_BUTTON_SIZE - RECIPE_BORDER_PADDING;
		}
	}

	@Override
	public void setShapeless() {
		this.shapelessIcon = new ShapelessIcon();
	}

	@Override
	@Nullable
	public IFocus<?> getFocus() {
		return focus;
	}

	@Nullable
	public RecipeTransferButton getRecipeTransferButton() {
		return recipeTransferButton;
	}

	@Nullable
	public RecipeBookmarkButton getRecipeBookmarkButton() {
		return recipeBookmarkButton;
	}

	@Override
	public IRecipeCategory getRecipeCategory() {
		return recipeCategory;
	}

	public int getPosX() {
		return posX;
	}

	public int getPosY() {
		return posY;
	}
}
