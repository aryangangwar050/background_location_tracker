import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:get/get.dart';
import 'location_record.dart';

class TrackingController extends GetxController with WidgetsBindingObserver {
  static const commands = MethodChannel('tracker/commands');
  static const updates = EventChannel('tracker/locations');

  final currentLocation = Rxn<LocationRecord>();
  final history = <LocationRecord>[].obs;
  final running = false.obs;
  final starting = false.obs;
  final loading = true.obs;
  final busy = false.obs;
  final error = RxnString();
  final notice = RxnString();
  final foregroundGranted = false.obs;
  final backgroundGranted = false.obs;
  final precise = false.obs;
  final notificationsGranted = true.obs;
  final locationEnabled = true.obs;
  StreamSubscription<dynamic>? _subscription;
  bool _refreshing = false;
  bool _refreshAgain = false;
  bool _disposed = false;

  @override
  void onInit() {
    super.onInit();
    WidgetsBinding.instance.addObserver(this);
    unawaited(initialize());
  }

  Future<void> initialize() async {
    await refreshData();
    if (_disposed) return;
    _subscription = updates.receiveBroadcastStream().listen(
      _onEvent,
      onError: (Object failure) {
        error.value = _message(failure);
      },
      onDone: () {
        if (!_disposed) {
          error.value =
              'Live updates disconnected. Reopen the app to reconnect.';
        }
      },
    );
    // Close the snapshot/subscription gap with a second native snapshot.
    await refreshData();
  }

  Future<void> refreshData() async {
    if (_refreshing) {
      _refreshAgain = true;
      return;
    }
    _refreshing = true;
    do {
      _refreshAgain = false;
      try {
        final status = await commands.invokeMapMethod<dynamic, dynamic>(
          'getTrackingStatus',
        );
        if (_disposed) break;
        if (status != null) _applyStatus(status);
        final last = await commands.invokeMapMethod<dynamic, dynamic>(
          'getLastLocation',
        );
        final records = await commands.invokeListMethod<dynamic>(
          'getLocationHistory',
        );
        if (_disposed) break;
        currentLocation.value =
            last == null ? null : LocationRecord.fromMap(last);
        history.assignAll(
          (records ?? []).map(
            (record) => LocationRecord.fromMap(record as Map),
          ),
        );
      } catch (failure) {
        if (!_disposed) error.value = _message(failure);
      } finally {
        loading.value = false;
      }
    } while (_refreshAgain && !_disposed);
    _refreshing = false;
  }

  void _applyStatus(Map<dynamic, dynamic> status) {
    running.value = status['running'] == true;
    starting.value = status['starting'] == true;
    foregroundGranted.value = status['foregroundGranted'] == true;
    precise.value = status['precise'] == true;
    backgroundGranted.value = status['backgroundGranted'] == true;
    notificationsGranted.value = status['notificationsGranted'] == true;
    locationEnabled.value = status['locationEnabled'] == true;
    error.value = status['error'] as String?;
    notice.value =
        status['requested'] == true && !running.value && !starting.value
            ? 'Tracking was interrupted. Tap Start to resume.'
            : null;
  }

  void _onEvent(dynamic event) {
    if (_disposed) return;
    try {
      final map = event as Map;
      if (map['type'] == 'status') {
        _applyStatus(map['status'] as Map);
      } else if (map['type'] == 'location') {
        final record = LocationRecord.fromMap(map['location'] as Map);
        if (currentLocation.value == null ||
            !record.timestamp.isBefore(currentLocation.value!.timestamp)) {
          currentLocation.value = record;
        }
        error.value = null;
        unawaited(refreshData());
      }
    } catch (failure) {
      error.value = 'Invalid native event: ${_message(failure)}';
    }
  }

  Future<void> startTracking() async {
    if (busy.value || running.value || starting.value) return;
    busy.value = true;
    error.value = null;
    try {
      final permission = await commands.invokeMapMethod<dynamic, dynamic>(
        'requestForegroundPermission',
      );
      if (permission != null) _applyStatus(permission);
      if (!foregroundGranted.value) {
        error.value =
            'Location permission is required. Open Settings to grant it.';
        return;
      }
      try {
        await commands.invokeMethod<dynamic>('requestNotificationPermission');
      } on PlatformException catch (failure) {
        if (failure.code != 'PERMISSION_DENIED') rethrow;
        notice.value =
            'Notifications denied. Android may show tracking only in its active apps panel.';
      }
      final status = await commands.invokeMapMethod<dynamic, dynamic>(
        'startTracking',
      );
      if (status != null) _applyStatus(status);
    } catch (failure) {
      error.value = _message(failure);
    } finally {
      busy.value = false;
    }
  }

  Future<void> stopTracking() async {
    if (busy.value) return;
    busy.value = true;
    error.value = null;
    try {
      final status = await commands.invokeMapMethod<dynamic, dynamic>(
        'stopTracking',
      );
      if (status != null) _applyStatus(status);
    } catch (failure) {
      error.value = _message(failure);
    } finally {
      busy.value = false;
    }
  }

  Future<void> requestBackgroundPermission() async {
    if (busy.value) return;
    busy.value = true;
    try {
      final status = await commands.invokeMapMethod<dynamic, dynamic>(
        'requestBackgroundPermission',
      );
      if (status != null) _applyStatus(status);
    } catch (failure) {
      error.value = _message(failure);
    } finally {
      busy.value = false;
    }
  }

  Future<void> openSettings({bool location = false}) async {
    try {
      await commands.invokeMethod<void>(
        location ? 'openLocationSettings' : 'openAppSettings',
      );
    } catch (failure) {
      error.value = _message(failure);
    }
  }

  String _message(Object failure) => failure is PlatformException
      ? failure.message ?? failure.code
      : failure.toString();

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(refreshData());
  }

  @override
  void onClose() {
    _disposed = true;
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_subscription?.cancel());
    super.onClose();
  }
}
