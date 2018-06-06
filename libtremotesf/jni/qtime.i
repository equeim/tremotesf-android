%naturalvar QTime;

class QTime;

// QTime
%typemap(jni) QTime "jobject"
%typemap(jtype) QTime "java.util.Date"
%typemap(jstype) QTime "java.util.Date"
%typemap(javadirectorin) QTime "$jniinput"
%typemap(javadirectorout) QTime "$javacall"

%typemap(in) QTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    $1 = QDateTime::fromMSecsSinceEpoch(time).time();
%}

%typemap(directorout) QTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    $result = QDateTime::fromMSecsSinceEpoch(time).time();
%}

%typemap(directorin,descriptor="Ljava/util/Date;") QTime
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $input = jenv->NewObject(clazz, id, $1.msecsSinceStartOfDay());
%}

%typemap(out) QTime
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $result = jenv->NewObject(clazz, id, $1.msecsSinceStartOfDay());
%}

%typemap(javain) QTime "$javainput"

%typemap(javaout) QTime
{
    return $jnicall;
}

%typemap(jni) const QTime& "jobject"
%typemap(jtype) const QTime& "java.util.Date"
%typemap(jstype) const QTime& "java.util.Date"
%typemap(javadirectorin) const QTime& "$jniinput"
%typemap(javadirectorout) const QTime& "$javacall"

%typemap(in) const QTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    QTime $1_str(QDateTime::fromMSecsSinceEpoch(time).time());
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    /* possible thread/reentrant code problem */
    static QTime $1_str;
    $1_str = QDateTime::fromMSecsSinceEpoch(time).time();
    $result = &$1_str;
%}

%typemap(directorin,descriptor="Ljava/util/Date;") const QTime&
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $input = jenv->NewObject(clazz, id, $1->msecsSinceStartOfDay());
%}

%typemap(out) const QTime& 
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $result = jenv->NewObject(clazz, id, $1->msecsSinceStartOfDay());
%}

%typemap(javain) const QTime& "$javainput"

%typemap(javaout) const QTime&
{
    return $jnicall;
}

