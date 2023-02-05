// SPDX-FileCopyrightText: 2017-2022 Alexey Rochev <equeim@gmail.com>
//
// SPDX-License-Identifier: GPL-3.0-or-later

%naturalvar QByteArray;

class QByteArray;

// QByteArray
%typemap(jni) QByteArray "jbyteArray"
%typemap(jtype) QByteArray "byte[]"
%typemap(jstype) QByteArray "byte[]"
%typemap(javadirectorin) QByteArray "$jniinput"
%typemap(javadirectorout) QByteArray "$javacall"

%typemap(in) QByteArray
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QByteArray");
        return $null;
    }
    jbyte* $1_pstr = jenv->GetByteArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    $1 = QByteArray::fromRawData(reinterpret_cast<const char*>($1_pstr), $1_len);
%}

%typemap(directorout) QByteArray
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QByteArray");
        return $null;
    }
    jbyte* $1_pstr = jenv->GetByteArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    $result = QByteArray::fromRawData(reinterpret_cast<const char*>($1_pstr), $1_len);
%}

%typemap(directorin) QByteArray
%{
    $input = jenv->NewByteArray(static_cast<jsize>($1.size()));
    jenv->SetByteArrayRegion($input, 0, static_cast<jsize>($1.size()), reinterpret_cast<const jbyte*>($1.constData()));
%}

%typemap(out) QByteArray
%{
    $result = jenv->NewByteArray(static_cast<jsize>($1.size()));
    jenv->SetByteArrayRegion($result, 0, static_cast<jsize>($1.size()), reinterpret_cast<const jbyte*>($1.constData()));
%}

%typemap(javain) QByteArray "$javainput"

%typemap(javaout) QByteArray
{
    return $jnicall;
}

%typemap(throws) QByteArray
%{
    const QByteArray message($1);
    SWIG_JavaThrowException(jenv, SWIG_JavaRuntimeException, message.toLocal8Bit().constData());
    return $null;
%}

%typemap(jni) const QByteArray& "jbyteArray"
%typemap(jtype) const QByteArray& "byte[]"
%typemap(jstype) const QByteArray& "byte[]"
%typemap(javadirectorin) const QByteArray& "$jniinput"
%typemap(javadirectorout) const QByteArray& "$javacall"

%typemap(in) const QByteArray&
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QByteArray");
        return $null;
    }
    jbyte* $1_pstr = jenv->GetByteArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    QByteArray $1_str(QByteArray::fromRawData(reinterpret_cast<const char*>($1_pstr), $1_len));
    $1 = &$1_str;
%}

%typemap(directorout,warning=SWIGWARN_TYPEMAP_THREAD_UNSAFE_MSG) const QByteArray& 
%{
    if(!$input) {
        SWIG_JavaThrowException(jenv, SWIG_JavaNullPointerException, "null QByteArray");
        return $null;
    }
    jbyte* $1_pstr = jenv->GetByteArrayElements($input, 0);
    if (!$1_pstr) return $null;
    jsize $1_len = jenv->GetArrayLength($input);
    /* possible thread/reentrant code problem */
    static QByteArray $1_str;
    $1_str = QByteArray::fromRawData(reinterpret_cast<const char*>($1_pstr), $1_len);
    $result = &$1_str;
%}

%typemap(directorin) const QByteArray&
%{
    $input = jenv->NewByteArray(static_cast<jsize>($1->size()));
    jenv->SetByteArrayRegion($input, 0, static_cast<jsize>($1->size()), reinterpret_cast<const jbyte*>($1->constData()));
%}

%typemap(out) const QByteArray& 
%{
    $result = jenv->NewByteArray(static_cast<jsize>($1->size()));
    jenv->SetByteArrayRegion($result, 0, static_cast<jsize>($1->size()), reinterpret_cast<const jbyte*>($1->constData()));
%}

%typemap(javain) const QByteArray& "$javainput"

%typemap(javaout) const QByteArray&
{
    return $jnicall;
}

%typemap(throws) const QByteArray&
%{
    const QByteArray message($1);
    SWIG_JavaThrowException(jenv, SWIG_JavaRuntimeException, message.constData());
    return $null;
%}

