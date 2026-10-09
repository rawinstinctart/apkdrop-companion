package de.rawinstinctai.apkdrop;

/** Security failures never become automatic retries or positive update claims. */
final class UpdateFailure {
    static boolean retryable(Exception error) {
        return !(error instanceof SecurityException) && (error instanceof java.io.IOException
                || error instanceof IllegalStateException);
    }
    static String message(Exception error) {
        if(error instanceof java.net.SocketTimeoutException) return "Zeitüberschreitung · Bitte erneut prüfen.";
        if(error instanceof java.net.UnknownHostException || error instanceof java.net.ConnectException)
            return "Offline oder Server nicht erreichbar · Letzter Prüfstand bleibt erhalten.";
        return error.getMessage()==null?"Prüfung fehlgeschlagen · Bitte erneut versuchen.":error.getMessage();
    }
}
