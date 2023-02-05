// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

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
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null String");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    if ($1_len) {
        static_assert(sizeof(char16_t) == sizeof(jchar));
        $1 = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorout) QString
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null String");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    if ($1_len) {
        static_assert(sizeof(char16_t) == sizeof(jchar));
        $result = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorin,descriptor="Ljava/lang/String;") QString
%{
    static_assert(sizeof(std::remove_pointer_t<decltype($1.utf16())>) == sizeof(jchar));
    $input = jenv->NewString(reinterpret_cast<const jchar*>($1.utf16()), static_cast<jsize>($1.size()));
%}

%typemap(out) QString
%{
    static_assert(sizeof(std::remove_pointer_t<decltype($1.utf16())>) == sizeof(jchar));
    $result = jenv->NewString(reinterpret_cast<const jchar*>($1.utf16()), static_cast<jsize>($1.size()));
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
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null String");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    static_assert(sizeof(char16_t) == sizeof(jchar));
    QString $1_str(QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len));
    $1 = &$1_str;
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QString&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null String");
        return $null;
    }
    const jchar* $1_pstr = jenv->GetStringChars($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetStringLength($input);
    /* possible thread/reentrant code problem */
    static QString $1_str{};
    if ($1_len) {
        static_assert(sizeof(char16_t) == sizeof(jchar));
        $1_str = QString::fromUtf16(reinterpret_cast<const char16_t*>($1_pstr), $1_len);
    }
    $result = &$1_str;
    jenv->ReleaseStringChars($input, $1_pstr);
%}

%typemap(directorin,descriptor="Ljava/lang/String;") const QString&
%{
    static_assert(sizeof(std::remove_pointer_t<decltype($1.utf16())>) == sizeof(jchar));
    $input = jenv->NewString(reinterpret_cast<const jchar*>($1.utf16()), static_cast<jsize>($1.size()));
%}

%typemap(out) const QString& 
%{
    static_assert(sizeof(std::remove_pointer_t<decltype($1->utf16())>) == sizeof(jchar));
    $result = jenv->NewString(reinterpret_cast<const jchar*>($1->utf16()), static_cast<jsize>($1->size()));
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

