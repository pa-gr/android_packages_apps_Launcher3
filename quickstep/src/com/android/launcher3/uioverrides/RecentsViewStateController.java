/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.OVERVIEW_ACTIONS;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.TASK_MODALNESS;
import static com.android.quickstep.views.RecentsView.TASK_PRIMARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.TaskView.FLAG_UPDATE_ALL;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.views.ClearAllButton;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;

/**
 * State handler for handling UI changes for {@link LauncherRecentsView}. In addition to managing
 * the basic view properties, this class also manages changes in the task visuals.
 */
@TargetApi(Build.VERSION_CODES.O)
public final class RecentsViewStateController extends
        BaseRecentsViewStateController<LauncherRecentsView> {

    public RecentsViewStateController(QuickstepLauncher launcher) {
        super(launcher);
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        super.setState(state);
        if (state.overviewUi) {
            mRecentsView.updateEmptyMessage();
            mRecentsView.resetTaskVisuals();
        }
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig(), state);
        mRecentsView.setFullscreenProgress(state.getOverviewFullscreenProgress());
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        mLauncher.getDepthController().setHasContentBehindLauncher(state.overviewUi);

        PendingAnimation builder =
                new PendingAnimation(state.getTransitionDuration(mLauncher, true));

        handleSplitSelectionState(state, builder, /* animate */false);
    }

    @Override
    void setStateWithAnimationInternal(@NonNull LauncherState toState,
            @NonNull StateAnimationConfig config, @NonNull PendingAnimation builder) {
        super.setStateWithAnimationInternal(toState, config, builder);

        if (toState.overviewUi) {
            // While animating into recents, update the visible task data as needed
            builder.addOnFrameCallback(() -> mRecentsView.loadVisibleTaskData(FLAG_UPDATE_ALL));
            mRecentsView.updateEmptyMessage();
            // TODO(b/238461210): Remove logging once root cause of flake detected.
            if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
                Log.d("b/238461210", "RecentsView#setStateWithAnimationInternal getCurrentPage(): "
                                + mRecentsView.getCurrentPage()
                                + ", getScrollForPage(getCurrentPage())): "
                                + mRecentsView.getScrollForPage(mRecentsView.getCurrentPage()));
            }
        } else {
            builder.addListener(
                    AnimatorListeners.forSuccessCallback(mRecentsView::resetTaskVisuals));
        }
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        builder.addListener(AnimatorListeners.forSuccessCallback(() ->
                mLauncher.getDepthController().setHasContentBehindLauncher(toState.overviewUi)));

        handleSplitSelectionState(toState, builder, /* animate */true);

        setAlphas(builder, config, toState);
        builder.setFloat(mRecentsView, FULLSCREEN_PROGRESS,
                toState.getOverviewFullscreenProgress(), LINEAR);
    }

    /**
     * Create or dismiss split screen select animations.
     * @param builder if null then this will run the split select animations right away, otherwise
     *                will add animations to builder.
     */
    private void handleSplitSelectionState(@NonNull LauncherState toState,
            @NonNull PendingAnimation builder, boolean animate) {
        PagedOrientationHandler orientationHandler =
                ((RecentsView) mLauncher.getOverviewPanel()).getPagedOrientationHandler();
        Pair<FloatProperty, FloatProperty> taskViewsFloat =
                orientationHandler.getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mLauncher.getDeviceProfile());

        if (toState == OVERVIEW_SPLIT_SELECT) {
            mRecentsView.createSplitSelectInitAnimation(builder,
                    toState.getTransitionDuration(mLauncher, true /* isToState */));
            // Add properties to shift remaining taskViews to get out of placeholder view
            builder.setFloat(mRecentsView, taskViewsFloat.first,
                    toState.getSplitSelectTranslation(mLauncher), LINEAR);
            builder.setFloat(mRecentsView, taskViewsFloat.second, 0, LINEAR);

            if (!animate) {
                builder.buildAnim().start();
            }

            mRecentsView.applySplitPrimaryScrollOffset();
        } else {
            mRecentsView.resetSplitPrimaryScrollOffset();
        }
    }

    private void setAlphas(PropertySetter propertySetter, StateAnimationConfig config,
            LauncherState state) {
        float clearAllButtonAlpha = state.areElementsVisible(mLauncher, CLEAR_ALL_BUTTON) ? 1 : 0;
        propertySetter.setFloat(mRecentsView.getClearAllButton(), ClearAllButton.VISIBILITY_ALPHA,
                clearAllButtonAlpha, LINEAR);
        float overviewButtonAlpha = state.areElementsVisible(mLauncher, OVERVIEW_ACTIONS) ? 1 : 0;
        propertySetter.setFloat(mLauncher.getActionsView().getVisibilityAlpha(),
                MultiValueAlpha.VALUE, overviewButtonAlpha, config.getInterpolator(
                        ANIM_OVERVIEW_ACTIONS_FADE, LINEAR));
    }

    @Override
    FloatProperty<RecentsView> getTaskModalnessProperty() {
        return TASK_MODALNESS;
    }

    @Override
    FloatProperty<RecentsView> getContentAlphaProperty() {
        return CONTENT_ALPHA;
    }
}
