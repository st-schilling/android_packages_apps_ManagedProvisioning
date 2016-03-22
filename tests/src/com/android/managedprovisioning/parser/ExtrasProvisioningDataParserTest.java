/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static com.android.managedprovisioning.parser.MessageParser.EXTRA_PROVISIONING_ACTION;
import static com.android.managedprovisioning.parser.MessageParser.EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM;
import static com.android.managedprovisioning.parser.MessageParser.EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

/** Tests for {@link ExtrasProvisioningDataParser}. */
@SmallTest
public class ExtrasProvisioningDataParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_SKIP_USER_SETUP = true;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");

    // Wifi info
    private static final String TEST_SSID = "TestWifi";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
    private static final WifiInfo TEST_WIFI_INFO = WifiInfo.Builder.builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN)
            .setSecurityType(TEST_SECURITY_TYPE)
            .setPassword(TEST_PASSWORD)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
            .setPacUrl(TEST_PAC_URL)
            .build();

    // Device admin package download info
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final int TEST_MIN_SUPPORT_VERSION = 17689;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                    .build();
    @Mock
    private Context mContext;

    private ExtrasProvisioningDataParser mExtrasProvisioningDataParser;

    private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        mExtrasProvisioningDataParser = new ExtrasProvisioningDataParser(mUtils = spy(new Utils()));
    }

    public void testParse_trustedSourceProvisioningIntent() throws Exception {
        // GIVEN a ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE is translated to
                        // ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        // THEN customizable color is not supported.
                        .setMainColor(ProvisioningParams.DEFAULT_MAIN_COLOR)
                        // THEN the trusted source is set to true.
                        .setStartedByTrustedSource(true)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_resumeProvisioningIntent() throws Exception {
        // GIVEN a resume provisioning intent which stores a device provisioning intent and other
        // extras.
        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING)
                .putExtra(EXTRA_PROVISIONING_ACTION, ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                // GIVEN the package checksum support sha1 is set to true.
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_SUPPORT_SHA1_PACKAGE_CHECKSUM, true)
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                // GIVEN this original provisioning intent was started by a trusted source.
                .putExtra(EXTRA_PROVISIONING_STARTED_BY_TRUSTED_SOURCE, true);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is restored to ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        .setDeviceAdminDownloadInfo(
                                PackageDownloadInfo.Builder.builder()
                                        .setLocation(TEST_DOWNLOAD_LOCATION)
                                        .setCookieHeader(TEST_COOKIE_HEADER)
                                        .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                        .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                                        .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                                        // THEN the package checksum support sha1 is set to true.
                                        .setPackageChecksumSupportsSha1(true)
                                        .build())
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        .setMainColor(TEST_MAIN_COLOR)
                        // THEN the trusted source is set to true.
                        .setStartedByTrustedSource(true)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_managedProfileIntent() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // GIVEN the device admin is installed.
        doReturn(TEST_COMPONENT_NAME)
                .when(mUtils)
                .findDeviceAdmin(TEST_PACKAGE_NAME, TEST_COMPONENT_NAME, mContext);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_PROFILE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        // THEN device admin package name is not supported.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_managedUserIntent() throws Exception {
        // GIVEN a managed user provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_USER)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_USER
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_USER)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        // THEN device admin package name is not supported in Managed User
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_managedDeviceIntent() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN Device Admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN time, time zone and locale are not supported.
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN wifi configuration is not supported.
                        .setWifiInfo(null)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_managedSharableDeviceIntent() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);

        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN Device Admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN time, time zone and locale are not supported.
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN wifi configuration is not supported.
                        .setWifiInfo(null)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build(),
                params);
    }

    public void testParse_nfcProvisioningIntentThrowsException() {
        // GIVEN a NFC provisioning intent and other extras.
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent, mContext);
            fail("ExtrasProvisioningDataParser doesn't support NFC intent. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    private static Bundle getTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtras() {
        Bundle timeTimezoneAndLocaleExtras = new Bundle();
        timeTimezoneAndLocaleExtras.putLong(EXTRA_PROVISIONING_LOCAL_TIME, TEST_LOCAL_TIME);
        timeTimezoneAndLocaleExtras.putString(EXTRA_PROVISIONING_TIME_ZONE, TEST_TIME_ZONE);
        timeTimezoneAndLocaleExtras.putString(
                EXTRA_PROVISIONING_LOCALE, MessageParser.localeToString(TEST_LOCALE));
        return timeTimezoneAndLocaleExtras;
    }

    private static Bundle getTestDeviceAdminDownloadExtras() {
        Bundle downloadInfoExtras = new Bundle();
        downloadInfoExtras.putInt(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE, TEST_MIN_SUPPORT_VERSION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, TEST_DOWNLOAD_LOCATION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER, TEST_COOKIE_HEADER);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                Base64.encodeToString(TEST_PACKAGE_CHECKSUM,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                Base64.encodeToString(TEST_SIGNATURE_CHECKSUM,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));
        return downloadInfoExtras;
    }
}
