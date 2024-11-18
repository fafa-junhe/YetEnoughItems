package mezz.jei.gui.overlay.bookmarks;

import com.google.gson.reflect.TypeToken;
import mezz.jei.Internal;
import mezz.jei.api.IBookmarkRecipeOverlay;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.config.Config;
import mezz.jei.gui.GuiHelper;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.input.IPaged;
import mezz.jei.input.IShowsRecipeFocuses;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import mezz.jei.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import org.w3c.dom.css.Rect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;

import static mezz.jei.gui.recipes.RecipeLayout.RECIPE_BORDER_PADDING;
import static mezz.jei.gui.recipes.RecipeLayout.RECIPE_BUTTON_SIZE;

public class BookmarkRecipeOverlay implements IShowsRecipeFocuses, ILeftAreaContent, IBookmarkRecipeOverlay, IPaged {

    private final List<IRecipeLayout> layouts = new ArrayList<>();
    private static final File bookmarkFile = Config.getBookmarkRecipeFile(); // Assume this method returns the file for saving and loading recipes

    private Rectangle parentArea = new Rectangle();
    private Rectangle displayArea = new Rectangle();
    private static Minecraft mc;
    private static final int RECIPE_PADDING = 20;
    private Set<Rectangle> guiExclusionAreas;
    private int currentPage = 0; // 当前页码，从0开始
    private final PageNavigation navigation;
    private static final int NAVIGATION_HEIGHT = 20;
    private List<List<Rectangle>> layoutPositionsPerPage;
    private boolean isLoaded = false;
    private List<IRecipeLayout> currentPageLayouts = new ArrayList<>();

    public BookmarkRecipeOverlay(GuiHelper guiHelper, GuiScreenHelper guiScreenHelper) {
        mc = Minecraft.getMinecraft();
        this.navigation = new PageNavigation(this, false);
        guiExclusionAreas = Collections.emptySet();
    }

    private static class RecipeStore {
        public String uid;
        public String inputs;
        public String outputs;
        public RecipeStore(String uid, String inputs, String outputs) {
            this.uid = uid;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        public String getUid() {
            return uid;
        }

        public void setUid(String uid) {
            this.uid = uid;
        }

        public String getInputs() {
            return inputs;
        }

        public void setInputs(String inputs) {
            this.inputs = inputs;
        }

        public String getOutputs() {
            return outputs;
        }

        public void setOutputs(String outputs) {
            this.outputs = outputs;
        }
    }

    // Source: https://github.com/vfyjxf/JEI-Utilities/blob/master/src/main/java/com/github/vfyjxf/jeiutilities/jei/bookmark/RecipeBookmarkList.java#L274
    @Nullable
    @SuppressWarnings("rawtypes")
    private Object getUnknownIngredientByUid(Collection<IIngredientType> ingredientTypes, String uid) {
        for (IIngredientType<?> ingredientType : ingredientTypes) {
            Object ingredient = Internal.getIngredientRegistry().getIngredientByUid(ingredientType, uid);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
    }

    // Source: https://github.com/vfyjxf/JEI-Utilities/blob/master/src/main/java/com/github/vfyjxf/jeiutilities/helper/IngredientHelper.java#L51
    public static <T> T getNormalize(@Nonnull T ingredient) {
        IIngredientHelper<T> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredient);
        T copy = LegacyUtil.getIngredientCopy(ingredient, ingredientHelper);
        if (copy instanceof ItemStack) {
            ((ItemStack) copy).setCount(1);
        } else if (copy instanceof FluidStack) {
            ((FluidStack) copy).amount = 1000;
        }
        return copy;
    }

    String mapIngredientsToString(Map<IIngredientType, List<List>> map) {
        StringBuilder result = new StringBuilder();
        for (IIngredientType iIngredientType : map.keySet()) {
            for (List outputs : map.get(iIngredientType)) {
                for (Object object : outputs){
                    if (object instanceof ItemStack){
                        result.append(((ItemStack) object).writeToNBT(new NBTTagCompound()));
                    }
                    else {
                        Object ingredient = getNormalize(object);
                        IIngredientHelper ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredient);
                        result.append(ingredientHelper.getUniqueId(ingredient));


                    }
                }
            }
        }
        return result.toString();
    }

    void saveBookmarks(){
        List<RecipeStore> recipeStores = new ArrayList<>();
        for (IRecipeLayout layout : layouts) {
            String uid = layout.getRecipeCategory().getUid();
            Ingredients is = new Ingredients();
            layout.getRecipeWrapper().getIngredients(is);
            Map<IIngredientType, List<List>> outputs = is.getOutputs();
            Map<IIngredientType, List<List>> inputs = is.getInputs();
            recipeStores.add(new RecipeStore(uid, mapIngredientsToString(inputs), mapIngredientsToString(outputs)));
        }
        try (Writer writer = new FileWriter(Config.getBookmarkRecipeFile())) {
            Internal.getRuntime().getGson().toJson(recipeStores, writer);
        }
        catch( IOException e ) {
            Log.get().error("Could not write config {}", Config.getBookmarkRecipeFile().getAbsolutePath(), e);
        }
    }

    void loadBookmarks() {
        try (Reader reader = new FileReader(Config.getBookmarkRecipeFile())) {
            Type listType = new TypeToken<List<RecipeStore>>() {
            }.getType();
            List<RecipeStore> recipeStores = Internal.getRuntime().getGson().fromJson(reader, listType);
            if (recipeStores == null){
                return;
            }
            layouts.clear();
            for (RecipeStore recipeStore : recipeStores) {
                IRecipeCategory recipeCategory = null;
                for (IRecipeCategory recipeCategory1 : Internal.getRuntime().getRecipeRegistry().getRecipeCategories()) {
                    if (recipeCategory1.getUid().equals(recipeStore.getUid())){
                        recipeCategory = recipeCategory1;
                        break;
                    }
                }
                assert recipeCategory != null;
                List<IRecipeWrapper> recipeWrappers = Internal.getRuntime().getRecipeRegistry().getRecipeWrappers(recipeCategory);
                for (IRecipeWrapper recipeWrapper : recipeWrappers) {
                    Ingredients is = new Ingredients();
                    recipeWrapper.getIngredients(is);
                    Map<IIngredientType, List<List>> outputs = is.getOutputs();
                    Map<IIngredientType, List<List>> inputs = is.getInputs();
                    String outputsString = mapIngredientsToString(outputs);
                    String inputsString = mapIngredientsToString(inputs);
                    if (outputsString.equals(recipeStore.getOutputs()) && inputsString.equals(recipeStore.getInputs())){
                        drop(RecipeLayout.create(0, recipeCategory, recipeWrapper, null, 0, 0));
                    }
                }

            }
        }
        catch( IOException e ) {
            Log.get().error("Could not read config {}", Config.getBookmarkRecipeFile().getAbsolutePath(), e);
        }
    }


    @Override
    public boolean drop(IRecipeLayout recipeLayout, int posX, int posY) {
        if (isMouseOver(posX, posY)){
            drop(recipeLayout);
            currentPage = layoutPositionsPerPage.size() - 1;
            return true;
        }
        return false;
    }

    public void drop(IRecipeLayout recipeLayout) {
        IRecipeLayout irl = RecipeLayout.create(0, (IRecipeCategory) recipeLayout.getRecipeCategory(), recipeLayout.getRecipeWrapper(), null, 0, 0);
        layouts.add(irl);
        updateBounds();
        saveBookmarks();

    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        final Rectangle backgroundRect = new Rectangle(displayArea.x, displayArea.y, displayArea.width, displayArea.height);
        return backgroundRect.contains(mouseX, mouseY);
    }

    // 显示占位内容
    private void drawPlaceholder(Minecraft minecraft, Rectangle area) {
        if (!isLoaded){
            loadBookmarks();
            isLoaded = true;
        }
        String message = "No recipes available.";
        int centerX = area.x + area.width / 2;
        int centerY = area.y + area.height / 2;
        minecraft.fontRenderer.drawString(message, centerX - minecraft.fontRenderer.getStringWidth(message) / 2, centerY, 0xFFFFFF);
    }

    private boolean isLayoutOccupiedByGuiExclusionArea(int x, int y, int layoutWidth, int layoutHeight, Set<Rectangle> guiExclusionAreas) {
        Rectangle layoutRect = new Rectangle(x, y, layoutWidth, layoutHeight);
        for (Rectangle exclusionArea : guiExclusionAreas) {
            if (layoutRect.intersects(exclusionArea)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void drawScreen(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (layouts.isEmpty()) {
            drawPlaceholder(minecraft, displayArea);
            return;
        }

        this.navigation.draw(minecraft, mouseX, mouseY, partialTicks);

        // Get the layouts for the current page
        List<Rectangle> currentPageLayouts = layoutPositionsPerPage.get(currentPage);

        // Draw the layouts for the current page
        for (Rectangle layoutPosition : currentPageLayouts) {
            // Get the layout corresponding to the current position
            int globalIndex = layoutPositionsPerPage.subList(0, currentPage).stream()
                    .mapToInt(List::size)
                    .sum() + currentPageLayouts.indexOf(layoutPosition);

            // Get the RecipeLayout from the global index
            RecipeLayout layout = (RecipeLayout) layouts.get(globalIndex);

            // Set position and draw
            layout.setPosition(layoutPosition.x, layoutPosition.y);
            layout.drawRecipe(minecraft, mouseX, mouseY);
            layout.drawOverlays(minecraft, mouseX, mouseY);
            layout.handleMouseInput();

            minecraft.fontRenderer.drawString(currentPageLayouts.indexOf(layoutPosition) + "", layoutPosition.x, layoutPosition.y, 255);
        }
    }




    @Override
    public void drawOnForeground(GuiContainer gui, int mouseX, int mouseY) {

    }

    @Override
    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
    }

    @Override
    public void updateBounds(Rectangle area, Set<Rectangle> guiExclusionAreas) {
        parentArea = area;
        this.guiExclusionAreas = guiExclusionAreas;
        updateBounds();
    }

    public void updateBounds() {
        displayArea = new Rectangle(parentArea);
        if (layouts.isEmpty()) {
            return;
        }

        // Calculate navigation area
        Rectangle estimatedNavigationArea = new Rectangle(displayArea.x, displayArea.y, displayArea.width, NAVIGATION_HEIGHT);
        Rectangle movedNavigationArea = MathUtil.moveDownToAvoidIntersection(guiExclusionAreas, estimatedNavigationArea);
        Rectangle navigationArea = new Rectangle(displayArea.x, movedNavigationArea.y, displayArea.width, NAVIGATION_HEIGHT);
        this.navigation.updateBounds(navigationArea);

        // Adjust display area Y coordinate
        displayArea.y = navigationArea.y + navigationArea.height + RECIPE_PADDING;

        // Find the narrowest and shortest layout as a reference
        int layoutWidth = Integer.MAX_VALUE;
        int layoutHeight = Integer.MAX_VALUE;
        for (IRecipeLayout layout : layouts) {
            int width = layout.getRecipeCategory().getBackground().getWidth();
            int height = layout.getRecipeCategory().getBackground().getHeight();
            layoutWidth = Math.min(layoutWidth, width);
            layoutHeight = Math.min(layoutHeight, height);
        }

        // Calculate layout positions dynamically
        List<List<Rectangle>> pageLayoutPositions = new ArrayList<>();
        List<Rectangle> occupiedAreas = new ArrayList<>();

        int startX = displayArea.x; // Assuming 3 columns
        int startY = displayArea.y;

        List<Rectangle> layoutPositionsForCurrentPage = new ArrayList<>();

        // Iterate through layouts and place them in pages
        int rowWidth = 0;
        int col = 0;
        int index = 0;
        int x = startX;
        int y = startY;
        for (IRecipeLayout recipeLayout : layouts) {
            RecipeLayout layout = (RecipeLayout) recipeLayout;


            Rectangle candidate;
            // Find the position for each layout
            do {
                int width = layout.getRecipeCategory().getBackground().getWidth() + RECIPE_PADDING;
                int height = layout.getRecipeCategory().getBackground().getHeight() + RECIPE_PADDING;
                candidate = new Rectangle(x, y, width, height);
                boolean intersects = false;

                if (isLayoutOccupiedByGuiExclusionArea(x, y, width, height, guiExclusionAreas)) {
                    intersects = true;
                } else {
                    for (Rectangle occupied : occupiedAreas) {
                        if (candidate.intersects(occupied)) {
                            intersects = true;
                            break;
                        }
                    }
                }

                if (!intersects) {
                    rowWidth += width;
                    col ++;
                    occupiedAreas.add((Rectangle) candidate.clone());
                    occupiedAreas.add(candidate);

                    break;
                }

                x += 16;  // Horizontal step
                if (x + width > displayArea.x + displayArea.width) {
                    if (col != 0 && col != 1 && layoutPositionsForCurrentPage.get(index - 1).x + layoutPositionsForCurrentPage.get(index - 1).width + (displayArea.width - rowWidth) / 2 <= displayArea.x + displayArea.width) {
                        for (int i = index - col; i < index; i++) {
                            layoutPositionsForCurrentPage.get(i).x += (displayArea.width - rowWidth) / 2;
                        }
                    }
                    col = 0;
                    rowWidth = 0;
                    x = startX; // Move to next row
                    y += height;
                }
                // If layout overflows, move it to the next page
                if (y + height > displayArea.y + displayArea.height - RECIPE_PADDING) {
                    index = 0;
                    rowWidth = 0;
                    col = 0;

                    pageLayoutPositions.add(layoutPositionsForCurrentPage); // Save the current page
                    layoutPositionsForCurrentPage = new ArrayList<>(); // Create a new page
                    occupiedAreas.clear();
                    x = startX; // Move to next row
                    y = startY;
                }
            } while (true);



            // Add the layout to the current page and mark it as occupied
            layoutPositionsForCurrentPage.add(candidate);
            index++;

        }


        // Add the last page if there are layouts on it
        if (!layoutPositionsForCurrentPage.isEmpty()) {
            for (int i = index - col; i < index; i++) {
                layoutPositionsForCurrentPage.get(i).x += (displayArea.width - rowWidth) / 2;

            }
            pageLayoutPositions.add(layoutPositionsForCurrentPage);
        }

        this.layoutPositionsPerPage = pageLayoutPositions;
        this.currentPage = Math.min(currentPage, pageLayoutPositions.size() - 1);
        this.navigation.updatePageState();
    }



    @Override
    public boolean handleMouseScrolled(int mouseX, int mouseY, int scrollDelta) {
        if (scrollDelta < 0) {
            this.nextPage();
            return true;
        } else if (scrollDelta > 0) {
            this.previousPage();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        // Get layouts for the current page
        List<Rectangle> currentPageLayouts = layoutPositionsPerPage.get(currentPage);

        // Handle mouse click for the current page's layouts
        for (Rectangle layoutPosition : currentPageLayouts) {
            // Get the layout corresponding to the current position
            int globalIndex = layoutPositionsPerPage.subList(0, currentPage).stream()
                    .mapToInt(List::size)
                    .sum() + currentPageLayouts.indexOf(layoutPosition);

            // Get the RecipeLayout from the global index
            RecipeLayout layout = (RecipeLayout) layouts.get(globalIndex);

            // Handle click if it's over this layout
            if (layout.handleClick(mc, mouseX, mouseY, mouseButton)) {
                return true;
            }
        }

        // Fall through to handle page navigation
        this.navigation.handleMouseClickedButtons(mouseX, mouseY);
        return false;
    }


    @Nullable
    @Override
    public IClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
        // Get layouts for the current page
        List<Rectangle> currentPageLayouts = layoutPositionsPerPage.get(currentPage);

        for (Rectangle layoutPosition : currentPageLayouts) {
            // Get the layout corresponding to the current position
            int globalIndex = layoutPositionsPerPage.subList(0, currentPage).stream()
                    .mapToInt(List::size)
                    .sum() + currentPageLayouts.indexOf(layoutPosition);

            // Get the RecipeLayout from the global index
            RecipeLayout layout = (RecipeLayout) layouts.get(globalIndex);

            // Check if the mouse is over this layout
            GuiIngredient<?> clicked = layout.getGuiIngredientUnderMouse(mouseX, mouseY);
            if (clicked != null) {
                Object displayedIngredient = clicked.getDisplayedIngredient();
                if (displayedIngredient != null) {
                    return ClickedIngredient.create(displayedIngredient, clicked.getRect());
                }
            }
        }
        return null;
    }


    @Override
    public boolean canSetFocusWithMouse() {
        return true;
    }

    @Override
    public boolean nextPage() {
        if (hasNext()) {
            currentPage++;
            this.navigation.updatePageState();

            return true;
        }
        return false;
    }

    @Override
    public boolean previousPage() {
        if (hasPrevious()) {
            currentPage--;
            this.navigation.updatePageState();

            return true;
        }
        return false;
    }

    @Override
    public boolean hasNext() {
        return currentPage + 1 < layoutPositionsPerPage.size();
    }

    @Override
    public boolean hasPrevious() {
        return currentPage > 0;
    }

    @Override
    public int getPageCount() {
        return layouts.isEmpty() ? 0 : layoutPositionsPerPage.size();
    }

    @Override
    public int getPageNumber() {
        return currentPage;
    }
}
