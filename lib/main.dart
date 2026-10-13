import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'location_record.dart';
import 'tracking_controller.dart';

void main() => runApp(const TrackerApp());

class TrackerBinding extends Bindings {
  @override
  void dependencies() => Get.lazyPut(TrackingController.new);
}

class TrackerApp extends StatelessWidget {
  const TrackerApp({super.key});
  @override
  Widget build(BuildContext context) => GetMaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'Location Tracker',
        theme: ThemeData(
          useMaterial3: true,
          colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF126D60)),
          scaffoldBackgroundColor: const Color(0xFFF5F7F8),
        ),
        initialRoute: '/',
        getPages: [
          GetPage(
            name: '/',
            page: () => const TrackingScreen(),
            binding: TrackerBinding(),
          ),
        ],
      );
}

class TrackingScreen extends GetView<TrackingController> {
  const TrackingScreen({super.key});

  String _time(DateTime time) => time.toLocal().toString().split('.').first;
  String _accuracy(LocationRecord? record) => record?.accuracy == null
      ? 'Unavailable'
      : '${record!.accuracy!.toStringAsFixed(1)} m';

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Location Tracker'),
          actions: [
            IconButton(
              tooltip: 'Refresh',
              onPressed: controller.refreshData,
              icon: const Icon(Icons.refresh),
            ),
            IconButton(
              tooltip: 'App settings',
              onPressed: controller.openSettings,
              icon: const Icon(Icons.settings_outlined),
            ),
          ],
        ),
        body: SafeArea(
          child: Obx(() {
            if (controller.loading.value) {
              return const Center(child: CircularProgressIndicator());
            }
            final location = controller.currentLocation.value;
            return RefreshIndicator(
              onRefresh: controller.refreshData,
              child: ListView(
                padding: const EdgeInsets.all(16),
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(
                                Icons.circle,
                                size: 12,
                                color: controller.running.value
                                    ? Colors.green
                                    : Colors.grey,
                              ),
                              const SizedBox(width: 8),
                              Text(
                                controller.running.value
                                    ? 'Running'
                                    : controller.starting.value
                                        ? 'Starting…'
                                        : 'Stopped',
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                            ],
                          ),
                          const SizedBox(height: 20),
                          Text(
                            'CURRENT LOCATION',
                            style: Theme.of(context).textTheme.labelMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            location == null
                                ? 'No location recorded yet'
                                : '${location.latitude.toStringAsFixed(6)}, ${location.longitude.toStringAsFixed(6)}',
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                          const SizedBox(height: 12),
                          Text('Accuracy: ${_accuracy(location)}'),
                          Text(
                            'Last updated: ${location == null ? '—' : _time(location.timestamp)}',
                          ),
                          const SizedBox(height: 20),
                          Wrap(
                            spacing: 12,
                            runSpacing: 8,
                            children: [
                              FilledButton.icon(
                                onPressed: controller.busy.value ||
                                        controller.running.value ||
                                        controller.starting.value
                                    ? null
                                    : controller.startTracking,
                                icon: const Icon(Icons.play_arrow),
                                label: const Text('Start Tracking'),
                              ),
                              OutlinedButton.icon(
                                onPressed: controller.busy.value
                                    ? null
                                    : controller.stopTracking,
                                icon: const Icon(Icons.stop),
                                label: const Text('Stop Tracking'),
                              ),
                            ],
                          ),
                          if (controller.busy.value)
                            const Padding(
                              padding: EdgeInsets.only(top: 12),
                              child: LinearProgressIndicator(),
                            ),
                        ],
                      ),
                    ),
                  ),
                  if (controller.error.value != null)
                    _message(context, controller.error.value!, error: true),
                  if (controller.notice.value != null)
                    _message(context, controller.notice.value!),
                  if (!controller.foregroundGranted.value)
                    _message(
                      context,
                      'Location permission is required. Tap Start to request it, or open Settings if permission was denied.',
                    ),
                  if (controller.foregroundGranted.value &&
                      !controller.precise.value)
                    _message(
                      context,
                      'Approximate location enabled. Tracking works with reduced accuracy; enable precise location in Settings if needed.',
                    ),
                  if (!controller.locationEnabled.value)
                    Card(
                      child: ListTile(
                        title: const Text('Device location is disabled'),
                        trailing: TextButton(
                          onPressed: () =>
                              controller.openSettings(location: true),
                          child: const Text('Enable'),
                        ),
                      ),
                    ),
                  if (!controller.notificationsGranted.value)
                    _message(
                      context,
                      'Notifications are denied. Tracking can continue, but its notification may be hidden. Enable notifications in Settings.',
                    ),
                  if (!controller.backgroundGranted.value)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Background location',
                              style: TextStyle(fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'A foreground service started here can track while minimized. “Allow all the time” also permits background location access during eligible service restarts. On Android 11+, choose Permissions → Location → Allow all the time in Settings.',
                            ),
                            const SizedBox(height: 8),
                            TextButton(
                              onPressed: !controller.foregroundGranted.value ||
                                      controller.busy.value
                                  ? null
                                  : controller.requestBackgroundPermission,
                              child: const Text('Allow background location'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  const SizedBox(height: 20),
                  Text(
                    'Recent locations (${controller.history.length}/50)',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: 8),
                  if (controller.history.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 24),
                      child: Text(
                        'No saved locations. Start tracking and wait for a location signal.',
                      ),
                    ),
                  ...controller.history.map(
                    (record) => Card(
                      child: ListTile(
                        leading: const Icon(Icons.location_on_outlined),
                        title: Text(
                          '${record.latitude.toStringAsFixed(6)}, ${record.longitude.toStringAsFixed(6)}',
                        ),
                        subtitle: Text(
                          '${_time(record.timestamp)}\nAccuracy: ${_accuracy(record)}',
                        ),
                        isThreeLine: true,
                      ),
                    ),
                  ),
                ],
              ),
            );
          }),
        ),
      );

  Widget _message(BuildContext context, String text, {bool error = false}) =>
      Card(
        color: error ? Theme.of(context).colorScheme.errorContainer : null,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(text),
              TextButton(
                onPressed: controller.openSettings,
                child: const Text('Settings'),
              ),
            ],
          ),
        ),
      );
}
