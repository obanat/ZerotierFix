# 保留行号信息，方便崩溃日志定位
-keepattributes SourceFile,LineNumberTable

# 保留注解
-keepattributes *Annotation*

# 保留泛型信息
-keepattributes Signature

# 保留异常
-keepattributes Exceptions

# ========== GreenDAO ==========
-keep class net.kaaass.zerotierfix.model.** { *; }
-keep class org.greenrobot.greendao.** { *; }
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
    public static java.lang.String TABLENAME;
}
-keep class **$Properties { *; }

# ========== EventBus ==========
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# ========== ZeroTier SDK ==========
-keep class com.zerotier.sdk.** { *; }

# ========== 序列化 ==========
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ========== Parcelable ==========
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ========== R 资源 ==========
-keepclassmembers class **.R$* {
    public static <fields>;
}

# ========== Lombok ==========
-dontwarn lombok.**

# ========== GreenDAO 可选依赖（未引入，忽略警告）==========
-dontwarn net.sqlcipher.**
-dontwarn rx.**

# ========== 高德地图 SDK ==========
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-dontwarn net.jafama.FastMath
