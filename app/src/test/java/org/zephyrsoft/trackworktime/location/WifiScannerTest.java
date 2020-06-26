package org.zephyrsoft.trackworktime.location;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import org.TestCommon;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.zephyrsoft.trackworktime.location.WifiScanner.Result.CANCEL_SPAMMING;
import static org.zephyrsoft.trackworktime.location.WifiScanner.Result.FAIL_RESULTS_NOT_UPDATED;
import static org.zephyrsoft.trackworktime.location.WifiScanner.Result.FAIL_SCAN_REQUEST_FAILED;
import static org.zephyrsoft.trackworktime.location.WifiScanner.Result.FAIL_WIFI_DISABLED;

@RunWith(MockitoJUnitRunner.class)
public class WifiScannerTest {

	@Mock
	private Context context;
	@Mock
	private WifiManager wifiManager;
	@Mock
	private WifiScanner wifiScanner;
	@Mock
	private WifiScanner.WifiScanListener wifiScanListener;

	@Before
	public void init(){
		context = mock(Context.class);

		wifiManager = mock(WifiManager.class);
		wifiScanner = Mockito.spy(new WifiScanner(wifiManager, 30, 30));

		wifiScanListener = mock(WifiScanner.WifiScanListener.class);
		wifiScanner.setWifiScanListener(wifiScanListener);
	}

	@Test
	public void register() {
		when(context.getApplicationContext()).thenReturn(context);

		wifiScanner.register(context);

		Assert.assertTrue(wifiScanner.isRegistered());
	}

	@Test
	public void unregister() {
		when(context.getApplicationContext()).thenReturn(context);

		wifiScanner.register(context);
		wifiScanner.unregister(context);

		Assert.assertTrue(!wifiScanner.isRegistered());
	}

	@Test
	public void onReceiveSuccess() throws Exception {
		TestCommon.mockBuildVersion(android.os.Build.VERSION_CODES.M);

		Intent intent = mock(Intent.class);
		when(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)).thenReturn(true);

		wifiScanner.onReceive(context, intent);

		verify(wifiScanner, times(1)).onWifiScanFinished(true);
	}

	@Test
	public void onReceiveFail() throws Exception {
		TestCommon.mockBuildVersion(android.os.Build.VERSION_CODES.M);

		Intent intent = mock(Intent.class);
		when(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)).thenReturn(false);

		wifiScanner.onReceive(context, intent);

		verify(wifiScanner, times(1)).onWifiScanFinished(false);
	}

	@Test
	public void onWifiScanFinishedSuccess() {
		ScanResult scanResult = mock(ScanResult.class);
		scanResult.SSID = "bananaWifi";
		List<ScanResult> scanResults = Collections.singletonList(scanResult);

		when(wifiManager.getScanResults()).thenReturn(scanResults);
		when(wifiManager.isWifiEnabled()).thenReturn(true);
		when(wifiManager.startScan()).thenReturn(true);

		wifiScanner.requestWifiScanResults();
		wifiScanner.onWifiScanFinished(true);

		verify(wifiScanListener, times(1)).onScanResultsUpdated(scanResults);
		verify(wifiScanListener, times(0)).onScanRequestFailed(any());
	}

	@Test
	public void onWifiScanFinishedFailed() {
		when(wifiManager.isWifiEnabled()).thenReturn(true);
		when(wifiManager.startScan()).thenReturn(true);

		wifiScanner.requestWifiScanResults();
		wifiScanner.onWifiScanFinished(false);

		verify(wifiScanListener, times(0)).onScanResultsUpdated(any());
		verify(wifiScanListener, times(1)).onScanRequestFailed(FAIL_RESULTS_NOT_UPDATED);
	}

	@Test
	public void requestWifiScanResultsWifiDisabled() {
		when(wifiManager.isWifiEnabled()).thenReturn(false);

		wifiScanner.requestWifiScanResults();

		verify(wifiScanListener, times(1)).onScanRequestFailed(any());
		verify(wifiScanListener, times(1)).onScanRequestFailed(FAIL_WIFI_DISABLED);
		verify(wifiScanListener, times(0)).onScanResultsUpdated(any());
	}

	@Test
	public void requestWifiScanResultsStartScanFailed() {
		when(wifiManager.isWifiEnabled()).thenReturn(true);
		when(wifiManager.startScan()).thenReturn(false);

		wifiScanner.requestWifiScanResults();

		verify(wifiScanListener, times(1)).onScanRequestFailed(any());
		verify(wifiScanListener, times(1)).onScanRequestFailed(FAIL_SCAN_REQUEST_FAILED);
		verify(wifiScanListener, times(0)).onScanResultsUpdated(any());
	}

	@Test
	public void requestWifiScanResultsCachedReturn() {
		ScanResult scanResult = mock(ScanResult.class);
		scanResult.SSID = "bananaWifi";
		List<ScanResult> scanResults = Collections.singletonList(scanResult);

		when(wifiManager.getScanResults()).thenReturn(scanResults);
		when(wifiManager.isWifiEnabled()).thenReturn(true);
		when(wifiManager.startScan()).thenReturn(true);
		when(wifiScanner.canScanAgain()).thenReturn(true);

		// Scan 1st time
		wifiScanner.requestWifiScanResults();
		wifiScanner.onWifiScanFinished(true);
		// Scan 2nd time, should return cached result
		wifiScanner.requestWifiScanResults();

		verify(wifiScanListener, times(2)).onScanResultsUpdated(scanResults);
		verify(wifiScanListener, times(0)).onScanRequestFailed(any());
		verify(wifiManager, times(1)).startScan();
		verify(wifiManager, times(1)).getScanResults();
	}

	@Test
	public void requestWifiScanResultsSpamming() {
		when(wifiManager.isWifiEnabled()).thenReturn(true);
		when(wifiScanner.canScanAgain()).thenReturn(false);

		wifiScanner.requestWifiScanResults();

		verify(wifiScanListener, times(1)).onScanRequestFailed(CANCEL_SPAMMING);
		verify(wifiScanListener, times(1)).onScanRequestFailed(any());
		verify(wifiManager, times(0)).startScan();
		verify(wifiManager, times(0)).getScanResults();
	}
}