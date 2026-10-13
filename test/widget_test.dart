import 'package:background_location_tracker/main.dart';
import 'package:background_location_tracker/tracking_controller.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:get/get.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final calls = <String>[];
  var denyLocation = false;
  var denyNotifications = false;
  var emptyDatabase = false;
  var failHistory = false;
  final status = <String, dynamic>{
    'running': false,
    'starting': false,
    'requested': false,
    'foregroundGranted': true,
    'precise': true,
    'backgroundGranted': true,
    'notificationsGranted': true,
    'locationEnabled': true,
    'error': null,
  };
  final record = <String, dynamic>{
    'id': 1,
    'latitude': 12.5,
    'longitude': 77.5,
    'accuracy': null,
    'timestamp': 1700000000000,
  };

  setUp(() {
    calls.clear();
    denyLocation = false;
    denyNotifications = false;
    emptyDatabase = false;
    failHistory = false;
    Get.testMode = true;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(TrackingController.commands, (call) async {
      calls.add(call.method);
      switch (call.method) {
        case 'getTrackingStatus':
          return status;
        case 'getLastLocation':
          return emptyDatabase ? null : record;
        case 'getLocationHistory':
          if (failHistory) {
            throw PlatformException(
                code: 'DATABASE_ERROR', message: 'Database unavailable');
          }
          return emptyDatabase ? [] : [record];
        case 'requestForegroundPermission':
          if (denyLocation) {
            throw PlatformException(
                code: 'PERMISSION_DENIED', message: 'Location denied');
          }
          return status;
        case 'requestNotificationPermission':
          if (denyNotifications) {
            throw PlatformException(
                code: 'PERMISSION_DENIED', message: 'Notifications denied');
          }
          return status;
        case 'startTracking':
          return {...status, 'running': true};
        case 'stopTracking':
          return status;
      }
      return null;
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('tracker/locations'),
      (_) async => null,
    );
  });

  tearDown(() async {
    Get.reset();
  });

  testWidgets(
    'Loads saved native data and controls tracking through channels',
    (tester) async {
      await tester.pumpWidget(const TrackerApp());
      await tester.pumpAndSettle();
      expect(calls.take(3), [
        'getTrackingStatus',
        'getLastLocation',
        'getLocationHistory',
      ]);
      expect(find.text('Stopped'), findsOneWidget);
      expect(find.text('Recent locations (1/50)'), findsOneWidget);
      expect(find.text('Accuracy: Unavailable'), findsOneWidget);
      await tester.tap(find.text('Start Tracking'));
      await tester.pumpAndSettle();
      expect(
        calls.indexOf('requestForegroundPermission'),
        lessThan(calls.indexOf('requestNotificationPermission')),
      );
      expect(
        calls.indexOf('requestNotificationPermission'),
        lessThan(calls.indexOf('startTracking')),
      );
      expect(find.text('Running'), findsOneWidget);
      await tester.tap(find.text('Stop Tracking'));
      await tester.pumpAndSettle();
      expect(find.text('Stopped'), findsOneWidget);
    },
  );

  testWidgets('Denied foreground permission never starts the service', (
    tester,
  ) async {
    denyLocation = true;
    await tester.pumpWidget(const TrackerApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Start Tracking'));
    await tester.pumpAndSettle();
    expect(calls, isNot(contains('startTracking')));
    expect(calls, isNot(contains('requestNotificationPermission')));
    expect(find.text('Location denied'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('Repeated live updates refresh history without duplicate rows', (
    tester,
  ) async {
    await tester.pumpWidget(const TrackerApp());
    await tester.pumpAndSettle();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final event = const StandardMethodCodec().encodeSuccessEnvelope({
      'type': 'location',
      'location': record,
    });
    await messenger.handlePlatformMessage('tracker/locations', event, (_) {});
    await messenger.handlePlatformMessage('tracker/locations', event, (_) {});
    await tester.pumpAndSettle();
    expect(find.text('Recent locations (1/50)'), findsOneWidget);
  });

  testWidgets(
      'Empty native database shows waiting state without fake coordinates',
      (tester) async {
    emptyDatabase = true;
    await tester.pumpWidget(const TrackerApp());
    await tester.pumpAndSettle();
    expect(find.text('No location recorded yet'), findsOneWidget);
    expect(find.text('Recent locations (0/50)'), findsOneWidget);
    expect(find.text('12.500000, 77.500000'), findsNothing);
  });

  testWidgets('Notification denial still permits a user-started service',
      (tester) async {
    denyNotifications = true;
    await tester.pumpWidget(const TrackerApp());
    await tester.pumpAndSettle();
    await tester.tap(find.text('Start Tracking'));
    await tester.pumpAndSettle();
    expect(calls, contains('startTracking'));
    expect(find.text('Running'), findsOneWidget);
  });

  testWidgets('Native database failure ends loading and offers Settings',
      (tester) async {
    failHistory = true;
    await tester.pumpWidget(const TrackerApp());
    await tester.pumpAndSettle();
    expect(find.text('Database unavailable'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Settings'), findsOneWidget);
  });
}
