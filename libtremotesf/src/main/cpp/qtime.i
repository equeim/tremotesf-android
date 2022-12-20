%naturalvar QTime;

class QTime;

// QTime
%typemap(jni) QTime "jlong"
%typemap(jtype) QTime "long"
%typemap(jstype) QTime "org.threeten.bp.LocalTime"
%typemap(javain) QTime "$javainput.toNanoOfDay()"
%typemap(javaout) QTime { return org.threeten.bp.LocalTime.ofNanoOfDay($jnicall); }
%typemap(javadirectorin) QTime "org.threeten.bp.LocalTime.ofNanoOfDay($jniinput)"
%typemap(javadirectorout) QTime "$javacall.toNanoOfDay()"

%typemap(in) QTime
%{
    $1 = QTime::fromMSecsSinceStartOfDay(static_cast<int>($input / jlong{1000000}));
%}

%typemap(directorout) QTime
%{
    $result = QTime::fromMSecsSinceStartOfDay(static_cast<int>($input / jlong{1000000}));
%}

%typemap(directorin,descriptor="J") QTime
%{
    $input = static_cast<jlong>($1.msecsSinceStartOfDay()) * jlong{1000000};
%}

%typemap(out) QTime
%{
    $result = static_cast<jlong>($1.msecsSinceStartOfDay()) * jlong{1000000};
%}

%typemap(jni) const QTime& "jlong"
%typemap(jtype) const QTime& "long"
%typemap(jstype) const QTime& "org.threeten.bp.LocalTime"
%typemap(javain) const QTime& "$javainput.toNanoOfDay()"
%typemap(javaout) const QTime& { return org.threeten.bp.LocalTime.ofNanoOfDay($jnicall); }
%typemap(javadirectorin) const QTime& "org.threeten.bp.LocalTime.ofNanoOfDay($jniinput)"
%typemap(javadirectorout) const QTime& "$javacall.toNanoOfDay()"

%typemap(in) const QTime&
%{
    auto $1_str = QTime::fromMSecsSinceStartOfDay(static_cast<int>($input / jlong{1000000}));
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QTime&
%{
    /* possible thread/reentrant code problem */
    static QTime $1_static{};
    $1_static = QTime::fromMSecsSinceStartOfDay(static_cast<int>($input / jlong{1000000}));
    $result = &$1_static;
%}

%typemap(directorin,descriptor="J") const QTime&
%{
    $input = static_cast<jlong>($1.msecsSinceStartOfDay()) * jlong{1000000};
%}

%typemap(out) const QTime& 
%{
    $result = static_cast<jlong>($1->msecsSinceStartOfDay()) * jlong{1000000};
%}


