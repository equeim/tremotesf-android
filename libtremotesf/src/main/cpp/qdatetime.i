%naturalvar QDateTime;

class QDateTime;

// QDateTime
%typemap(jni) QDateTime "jlong"
%typemap(jtype) QDateTime "long"
%typemap(jstype) QDateTime "org.threeten.bp.Instant"
%typemap(javain) QDateTime "$javainput.toEpochMilli()"
%typemap(javaout) QDateTime { return org.threeten.bp.Instant.ofEpochMilli($jnicall); }
%typemap(javadirectorin) QDateTime "org.threeten.bp.Instant.ofEpochMilli($jniinput)"
%typemap(javadirectorout) QDateTime "$javacall.toEpochMilli()"

%typemap(in) QDateTime
%{
    $1 = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
%}

%typemap(directorout) QDateTime
%{
    $result = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
%}

%typemap(directorin,descriptor="J") QDateTime
%{
    $input = static_cast<jlong>($1.toMSecsSinceEpoch());
%}

%typemap(out) QDateTime
%{
    $result = static_cast<jlong>($1.toMSecsSinceEpoch());
%}

%typemap(jni) const QDateTime& "jlong"
%typemap(jtype) const QDateTime& "long"
%typemap(jstype) const QDateTime& "org.threeten.bp.Instant"
%typemap(javain) const QDateTime& "$javainput.toEpochMilli()"
%typemap(javaout) const QDateTime& { return org.threeten.bp.Instant.ofEpochMilli($jnicall); }
%typemap(javadirectorin) const QDateTime& "org.threeten.bp.Instant.ofEpochMilli($jniinput)"
%typemap(javadirectorout) const QDateTime& "$javacall.toEpochMilli()"

%typemap(in) const QDateTime&
%{
    auto $1_str = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QDateTime&
%{
    /* possible thread/reentrant code problem */
    static QDateTime $1_static{};
    $1_static = QDateTime::fromMSecsSinceEpoch(static_cast<qint64>($input), Qt::UTC);
    $result = &$1_static;
%}

%typemap(directorin,descriptor="J") const QDateTime&
%{
    $input = static_cast<jlong>($1.toMSecsSinceEpoch());
%}

%typemap(out) const QDateTime& 
%{
    $result = static_cast<jlong>($1->toMSecsSinceEpoch());
%}


