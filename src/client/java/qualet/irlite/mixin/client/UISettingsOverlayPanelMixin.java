package qualet.irlite.mixin.client;

import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.ui.patcher.UIPatcherSection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NOTE: The field that tracks the currently-selected settings category has
 * had a different name in every CML build we've checked so far ("category"
 * as a ValueGroup, then "selectedCategoryId" as a String — neither matched
 * the actual installed jar). Rather than hardcode another guess via
 * @Shadow (which hard-crashes the whole class if wrong), we look it up by
 * reflection at runtime against a list of candidates, and degrade
 * gracefully (show the patcher section under every category instead of just
 * "irlite_patcher") if none of them match. This trades a little extra
 * runtime lookup cost for never being able to break game launch again.
 */
@Mixin(UISettingsOverlayPanel.class)
public abstract class UISettingsOverlayPanelMixin
{
    @Shadow public UIScrollView options;

    @Shadow public abstract void refresh();

    private static final String[] CANDIDATE_FIELD_NAMES = {
        "selectedCategoryId", "categoryId", "currentCategoryId",
        "activeCategoryId", "selectedCategory", "category", "currentCategory"
    };

    @Inject(method = "refresh", at = @At("TAIL"))
    private void irlite$appendPatcher(CallbackInfo ci)
    {

        if (this.options == null)
        {
            return;
        }

        Boolean onPatcherCategory = irlite$isOnPatcherCategory();

        /* Could not determine which category is selected: degrade gracefully
           by showing the patcher section regardless of category, instead of
           hiding it entirely or crashing. */
        if (onPatcherCategory != null && !onPatcherCategory)
        {
            return;
        }

        UIPatcherSection.append(this.options, this::refresh);
        this.options.resize();
    }

    /**
     * @return true if we're confident the irlite_patcher category is selected,
     *         false if we're confident it's NOT selected, or null if we
     *         couldn't determine it at all (unknown field layout).
     */
    private Boolean irlite$isOnPatcherCategory()
    {
        for (String name : CANDIDATE_FIELD_NAMES)
        {
            try
            {
                Field field = UISettingsOverlayPanel.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(this);

                if (value == null)
                {
                    continue;
                }

                if (value instanceof String str)
                {
                    return "irlite_patcher".equals(str);
                }

                /* Possibly a ValueGroup-like object with a getId() method */
                try
                {
                    Method getId = value.getClass().getMethod("getId");
                    Object id = getId.invoke(value);

                    if (id instanceof String idStr)
                    {
                        return "irlite_patcher".equals(idStr);
                    }
                }
                catch (Throwable ignored) {}
            }
            catch (NoSuchFieldException ignored)
            {
                /* try next candidate */
            }
            catch (Throwable ignored)
            {
                /* field existed but reading it failed; try next candidate */
            }
        }

        return null;
    }
}
