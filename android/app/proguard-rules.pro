# Models are encoded explicitly with org.json; no reflective serializers.
-keepattributes Signature,*Annotation*
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
