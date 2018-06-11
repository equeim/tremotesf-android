%naturalvar QTime;

class QTime;

// QTime
%typemap(jni) QTime "jint"
%typemap(jtype) QTime "int"
%typemap(jstype) QTime "int"
%typemap(javadirectorin) QTime "$jniinput"
%typemap(javadirectorout) QTime "$javacall"

%typemap(in) QTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    $1 = QTime::fromMSecsSinceStartOfDay($input * 60 * 1000);
%}

%typemap(directorout) QTime
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    $result = QTime::fromMSecsSinceStartOfDay($input * 60 * 1000);
%}

%typemap(directorin,descriptor="Ljava/util/Date;") QTime
%{
    $input = $1.msecsSinceStartOfDay() / (60 * 1000);
%}

%typemap(out) QTime
%{
    $result = $1.msecsSinceStartOfDay() / (60 * 1000);
%}

%typemap(javain) QTime "$javainput"

%typemap(javaout) QTime
{
    return $jnicall;
}

%typemap(jni) const QTime& "jint"
%typemap(jtype) const QTime& "int"
%typemap(jstype) const QTime& "int"
%typemap(javadirectorin) const QTime& "$jniinput"
%typemap(javadirectorout) const QTime& "$javacall"

%typemap(in) const QTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    QTime $1_str(QTime::fromMSecsSinceStartOfDay($input * 60 * 1000));
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QTime&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QTime");
        return $null;
    }
    /* possible thread/reentrant code problem */
    static QTime $1_str;
    $1_str = QTime::fromMSecsSinceStartOfDay($input * 60 * 1000);
    $result = &$1_str;
%}

%typemap(directorin,descriptor="Ljava/util/Date;") const QTime&
%{
    $input = $1->msecsSinceStartOfDay() / (60 * 1000);
%}

%typemap(out) const QTime& 
%{
    $result = $1->msecsSinceStartOfDay() / (60 * 1000);
%}

%typemap(javain) const QTime& "$javainput"

%typemap(javaout) const QTime&
{
    return $jnicall;
}

