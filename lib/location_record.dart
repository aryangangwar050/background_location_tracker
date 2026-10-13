class LocationRecord {
  const LocationRecord({
    required this.id,
    required this.latitude,
    required this.longitude,
    required this.accuracy,
    required this.timestamp,
  });

  factory LocationRecord.fromMap(Map<dynamic, dynamic> map) => LocationRecord(
        id: (map['id'] as num).toInt(),
        latitude: (map['latitude'] as num).toDouble(),
        longitude: (map['longitude'] as num).toDouble(),
        accuracy: (map['accuracy'] as num?)?.toDouble(),
        timestamp: DateTime.fromMillisecondsSinceEpoch(
          (map['timestamp'] as num).toInt(),
        ),
      );

  final int id;
  final double latitude;
  final double longitude;
  final double? accuracy;
  final DateTime timestamp;
}
