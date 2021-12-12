%naturalvar QString;

class QString;

// QString
%typemap(jni) QString "jstring"
%typemap(jtype) QString "String"
%typemap(jstype) QString "String"
%typemap(javadirectorin) QString "$jniinput"
%typemap(javadirectorout) QString "$javacall"

%typemap(in) QString
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QString");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    if ($1_len) {
        $1 = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorout) QString
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QString");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    if ($1_len) {
        $result = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorin,descriptor="Ljava/lang/String;") QString
%{
    $input = jenv->NewString($1.utf16(), static_cast<jsize>($1.size()));
%}

%typemap(out) QString
%{
    $result = jenv->NewString($1.utf16(), static_cast<jsize>($1.size()));
%}

%typemap(javain) QString "$javainput"

%typemap(javaout) QString
{
    return $jnicall;
}

%typemap(throws) QString
%{
    const QString message($1);
    SWIG_JavaThrowException(jenv, SWIG_JavaRuntimeException, message.toLocal8Bit().constData());
    return $null;
%}

%typemap(jni) const QString& "jstring"
%typemap(jtype) const QString& "String"
%typemap(jstype) const QString& "String"
%typemap(javadirectorin) const QString& "$jniinput"
%typemap(javadirectorout) const QString& "$javacall"

%typemap(in) const QString&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QString");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    QString $1_str(QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len));
    $1 = &$1_str;
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QString&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QString");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    /* possible thread/reentrant code problem */
    static QString $1_str;
    if ($1_len) {
        $1_str = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    $result = &$1_str;
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorin,descriptor="Ljava/lang/String;") const QString&
%{
    $input = jenv->NewString($1.utf16(), static_cast<jsize>($1.size()));
%}

%typemap(out) const QString& 
%{
    $result = jenv->NewString($1->utf16(), static_cast<jsize>($1->size()));
%}

%typemap(javain) const QString& "$javainput"

%typemap(javaout) const QString&
{
    return $jnicall;
}

%typemap(throws) const QString&
%{
    const QString message($1);
    SWIG_JavaThrowException(jenv, SWIG_JavaRuntimeException, message.toLocal8Bit().constData());
    return $null;
%}

