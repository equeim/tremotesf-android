%naturalvar QDateTime;

class QDateTime;

// QDateTime
%typemap(jni) QDateTime "jobject"
%typemap(jtype) QDateTime "java.util.Date"
%typemap(jstype) QDateTime "java.util.Date"
%typemap(javadirectorin) QDateTime "$jniinput"
%typemap(javadirectorout) QDateTime "$javacall"

%typemap(in) QDateTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QDateTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    $1 = QDateTime::fromMSecsSinceEpoch(time);
%}

%typemap(directorout) QDateTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QDateTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    $result = QDateTime::fromMSecsSinceEpoch(time);
%}

%typemap(directorin,descriptor="Ljava/util/Date;") QDateTime
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $input = jenv->NewObject(clazz, id, $1.toMSecsSinceEpoch());
%}

%typemap(out) QDateTime
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $result = jenv->NewObject(clazz, id, $1.toMSecsSinceEpoch());
%}

%typemap(javain) QDateTime "$javainput"

%typemap(javaout) QDateTime
{
    return $jnicall;
}

%typemap(jni) const QDateTime& "jobject"
%typemap(jtype) const QDateTime& "java.util.Date"
%typemap(jstype) const QDateTime& "java.util.Date"
%typemap(javadirectorin) const QDateTime& "$jniinput"
%typemap(javadirectorout) const QDateTime& "$javacall"

%typemap(in) const QDateTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QDateTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    QDateTime $1_str(QDateTime::fromMSecsSinceEpoch(time));
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QDateTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QDateTime");
        return $null;
    }
    const jmethodID id = jenv->GetMethodID(jenv->GetObjectClass($input), "getTime", "()J");
    const jlong time = jenv->CallLongMethod($input, id);
    /* possible thread/reentrant code problem */
    static QDateTime $1_str;
    $1_str = QDateTime::fromMSecsSinceEpoch(time);
    $result = &$1_str;
%}

%typemap(directorin,descriptor="Ljava/util/Date;") const QDateTime&
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $input = jenv->NewObject(clazz, id, $1->toMSecsSinceEpoch());
%}

%typemap(out) const QDateTime& 
%{
    const jclass clazz = jenv->FindClass("java/util/Date");
    const jmethodID id = jenv->GetMethodID(clazz, "<init>", "(J)V");
    $result = jenv->NewObject(clazz, id, $1->toMSecsSinceEpoch());
%}

%typemap(javain) const QDateTime& "$javainput"

%typemap(javaout) const QDateTime&
{
    return $jnicall;
}

