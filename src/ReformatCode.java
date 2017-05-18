import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;
import java.util.UUID;

public class ReformatCode extends AnAction {

    private byte[] reformatFile(String path) throws InterruptedException, IOException {
        Process p = Runtime.getRuntime().exec(new String[]{
                "sh", "-c",
                String.format("yapf '%s'", path)
        });
        p.waitFor();

        // read the formatted content
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        InputStream inputStream = p.getInputStream();

        int read = 0;
        byte[] bytes = new byte[1024];

        while ((read = inputStream.read(bytes)) != -1) {
            byteArrayOutputStream.write(bytes, 0, read);
        }

        return byteArrayOutputStream.toByteArray();
    }

    private void writeFileContent(InputStream inputStream, OutputStream outputStream) throws IOException {
        int read = 0;
        byte[] bytes = new byte[1024];

        while ((read = inputStream.read(bytes)) != -1) {
            outputStream.write(bytes, 0, read);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        // extract current open file, it could be file or folder or null it doesn't get focus
        VirtualFile virtualFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);

        if (virtualFile == null || virtualFile.isDirectory()) {
            return;
        }

        String path = virtualFile.getPath();

        if (!path.endsWith(".py")) {
            return;
        }

        if (!virtualFile.isWritable()) {
            return;
        }

        try {
            // save changes so that they don't invoke message box that need to synchronize file
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            Document document = fileDocumentManager.getDocument(virtualFile);
            fileDocumentManager.saveDocument(document);

//            // read its content and put it to the temporary file
//            InputStream inputStream = virtualFile.getInputStream();
//            File tmpFile = new File(String.format("/tmp/%s.py", UUID.randomUUID().toString()));
//            this.writeFileContent(inputStream, new FileOutputStream(tmpFile));

            // reformat it using Google/YAPF
            byte[] formattedContent = this.reformatFile(virtualFile.getPath());

            // unlock the file & write changes
            Application app = ApplicationManager.getApplication();
            app.runWriteAction(() -> {
                try {
                    virtualFile.setBinaryContent(formattedContent);
                    virtualFile.refresh(false, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
