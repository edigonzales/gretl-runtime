import ch.so.agi.gretl.runtime.api.InputField;
import ch.so.agi.gretl.runtime.api.OperationType;
import ch.so.agi.gretl.tasks.io.http.HttpDownloadOperation;

/** Runtime control: the actual current HttpDownload converter accepts a full https URL. */
public final class P2HttpRuntimeControl {
    public static void main(String[] args) {
        OperationType<?, ?> type = HttpDownloadOperation.type();
        InputField<?, ?> url = type.inputs().stream().filter(i -> i.name().equals("url")).findFirst().orElseThrow();
        InputField<?, ?> method = type.inputs().stream().filter(i -> i.name().equals("method")).findFirst().orElseThrow();
        Object convertedUrl = url.convert().apply("https://example.invalid/data.zip");
        Object convertedMethod = method.convert().apply("GET");
        System.out.println("url.convert=" + convertedUrl + " class=" + convertedUrl.getClass().getName());
        System.out.println("method.convert=" + convertedMethod);
        System.out.println("RUNTIME CONTROL ACCEPTED https URL");
    }
}
