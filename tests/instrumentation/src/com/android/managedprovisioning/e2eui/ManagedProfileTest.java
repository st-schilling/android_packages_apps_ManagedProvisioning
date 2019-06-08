/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.managedprovisioning.e2eui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.View;

import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.base.DefaultFailureHandler;
import androidx.test.filters.LargeTest;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.TestInstrumentationRunner;
import com.android.managedprovisioning.common.BlockingBroadcastReceiver;
import com.android.managedprovisioning.preprovisioning.PreProvisioningActivity;

import org.hamcrest.Matcher;

import java.util.List;

@LargeTest
public class ManagedProfileTest extends AndroidTestCase {
    private static final String TAG = "ManagedProfileTest";

    private static final long TIMEOUT_SECONDS = 120L;
    private static final long WAIT_EDU_SCREENS_MILLIS = 60000L;

    public ActivityTestRule mActivityRule;
    private ProvisioningResultListener mResultListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivityRule = new ActivityTestRule<>(
                PreProvisioningActivity.class,
                true /* initialTouchMode */,
                false);  // launchActivity. False to set intent per method
        mResultListener = new ProvisioningResultListener(getContext());
        TestInstrumentationRunner.registerReplacedActivity(PreProvisioningActivity.class,
                (cl, className, intent) -> new TestPreProvisioningActivity(mResultListener));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        TestInstrumentationRunner.unregisterReplacedActivity(PreProvisioningActivity.class);
        mResultListener.unregister();

        // Remove any managed profiles in case that
        removeAllManagedProfiles();
    }

    private void removeAllManagedProfiles() {
        UserManager um = getContext().getSystemService(UserManager.class);
        List<UserInfo> users = um.getUsers();
        for (UserInfo user : users) {
            if (user.isManagedProfile()) {
                int userId = user.getUserHandle().getIdentifier();
                removeProfileAndWait(userId);
            }
        }
    }

    /** Remove a profile and wait until it has been removed before continuing. */
    private void removeProfileAndWait(int userId) {
        Log.e(TAG, "remove managed profile user: " + userId);
        UserManager userManager = getContext().getSystemService(UserManager.class);

        // Intent.ACTION_MANAGED_PROFILE_REMOVED gets sent too early, so we need to wait for
        // Intent.ACTION_USER_REMOVED
        BlockingBroadcastReceiver receiver =
                new BlockingBroadcastReceiver(mContext, Intent.ACTION_USER_REMOVED);
        try {
            receiver.register();
            userManager.removeUserEvenWhenDisallowed(userId);

            long timeoutMillis = TIMEOUT_SECONDS * 1000;
            Intent confirmation = receiver.awaitForBroadcast(timeoutMillis);

            if (confirmation == null) {
                // The user was not removed
                fail("Waiting for profile to be removed, but was not removed.");
            }
        } finally {
            receiver.unregisterQuietly();
        }
    }

    @FlakyTest(bugId=121307452)
    public void testManagedProfile() throws Exception {
        mActivityRule.launchActivity(ManagedProfileAdminReceiver.INTENT_PROVISION_MANAGED_PROFILE);

        // Retry pressing the "Accept & continue" button twice to reduce flakiness
        new EspressoClickRetryActions(2) {
            @Override
            public ViewInteraction newViewInteraction1() {
                return onView(allOf(withClassName(containsString("FooterActionButton")),
                        withText(R.string.accept_and_continue)));
            }
        }.run();

        Thread.sleep(WAIT_EDU_SCREENS_MILLIS);
        mResultListener.register();

        // Retry pressing the "Next" button twice to reduce flakiness
        new EspressoClickRetryActions(2) {
            @Override
            public ViewInteraction newViewInteraction1() {
                return onView(allOf(withClassName(containsString("FooterActionButton")),
                        withText(R.string.next)));
            }
        }.run();

        if (mResultListener.await(TIMEOUT_SECONDS)) {
            assertTrue(mResultListener.getResult());
        } else {
            fail("timeout: " + TIMEOUT_SECONDS + " seconds");
        }
    }

    private abstract class EspressoClickRetryActions {
        private final int mRetries;
        private int i = 0;

        EspressoClickRetryActions(int retries) {
            mRetries = retries;
        }

        public abstract ViewInteraction newViewInteraction1();

        public void run() {
            i++;
            newViewInteraction1()
                    .withFailureHandler(this::handleFailure)
                    .perform(click());
            Log.i(TAG, "newViewInteraction1 succeeds.");
        }

        private void handleFailure(Throwable e, Matcher<View> matcher) {
            Log.i(TAG, "espresso handleFailure count: " + i, e);
            if (i < mRetries) {
                run();
            } else {
                new DefaultFailureHandler(getContext()).handle(e, matcher);
            }
        }
    }
}

