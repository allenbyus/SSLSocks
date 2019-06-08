package link.infra.sslsocks.gui.keymgmt;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import link.infra.sslsocks.R;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class KeyEditActivity extends AppCompatActivity {

	private final int IMPORT_FILE = 2;
	private EditText fileContents;
	private EditText fileName;
	private String existingFileName;
	public static final String ARG_EXISTING_FILE_NAME = "EXISTING_FILE_NAME";

	private static final String TAG = KeyEditActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_key_edit);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

		fileContents = findViewById(R.id.file_contents);
		fileName = findViewById(R.id.file_name);

		Intent intent = getIntent();
		if (intent != null) {
			existingFileName = intent.getStringExtra(ARG_EXISTING_FILE_NAME);
		}
		if (existingFileName == null) {
			getSupportActionBar().setTitle(R.string.title_activity_key_create);
			findViewById(R.id.import_button).setVisibility(View.VISIBLE);
			findViewById(R.id.delete_button).setVisibility(View.GONE);
		} else {
			fileName.setText(existingFileName);
			openFile();
		}
	}


	public void importExternalFile(View view) {
		importExternalFile();
	}

	private void importExternalFile() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		Intent chooserIntent = Intent.createChooser(intent, getString(R.string.title_activity_config_editor));
		startActivityForResult(chooserIntent, IMPORT_FILE);
	}

	// Get the file name for importing a file from a Uri
	private String getFileName(Uri uri) {
		String result = null;
		if ("content".equals(uri.getScheme())) {
			try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
				if (cursor != null && cursor.moveToFirst()) {
					result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
				}
			}
		}
		if (result == null) {
			result = Objects.requireNonNull(uri.getPath());
			int cut = result.lastIndexOf('/');
			if (cut != -1) {
				result = result.substring(cut + 1);
			}
		}
		return result;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == IMPORT_FILE) {
			if (resultCode == RESULT_OK) {
				Uri fileData = data.getData();
				if (fileData != null) {
					InputStream inputStream;
					try {
						inputStream = getContentResolver().openInputStream(fileData);
						if (inputStream == null) { // Just to keep the linter happy that I'm doing null checks
							throw new FileNotFoundException();
						}
					} catch (FileNotFoundException e) {
						Log.e(TAG, "Failed to read imported file", e);
						Toast.makeText(this, R.string.file_read_fail, Toast.LENGTH_SHORT).show();
						return;
					}
					try (BufferedSource in = Okio.buffer(Okio.source(inputStream))) {
						fileContents.setText(in.readUtf8());
					} catch (IOException e) {
						Log.e(TAG, "Failed to read imported file", e);
						Toast.makeText(this, R.string.file_read_fail, Toast.LENGTH_SHORT).show();
						return;
					}
					fileName.setText(getFileName(fileData));
				}
			}
		}
	}

	public void saveFile(View view) {
		String fileNameString = fileName.getText().toString();
		if (fileNameString.length() < 1) {
			Toast.makeText(this, R.string.file_name_required, Toast.LENGTH_SHORT).show();
			return;
		}
		if (!fileNameString.endsWith(".pem") && !fileNameString.endsWith(".p12")) {
			Toast.makeText(this, R.string.file_name_ext, Toast.LENGTH_SHORT).show();
			return;
		}
		if (fileNameString.contains("/")) {
			Toast.makeText(this, R.string.file_name_slashes, Toast.LENGTH_SHORT).show();
			return;
		}

		File file = new File(getFilesDir().getPath() + "/" + fileNameString);
		try (BufferedSink out = Okio.buffer(Okio.sink(file))) {
			String pendingContent = fileContents.getText().toString();
			out.writeUtf8(pendingContent);
			out.close();
			// If renamed, delete old file
			if (existingFileName != null && existingFileName.length() > 0 && !existingFileName.equals(fileNameString)) {
				File existingFile = new File(getFilesDir().getPath() + "/" + existingFileName);
				//noinspection ResultOfMethodCallIgnored
				existingFile.delete();
			}
			setResult(RESULT_OK);
			finish();
		} catch (IOException e) {
			Toast.makeText(this, R.string.file_write_fail, Toast.LENGTH_SHORT).show();
			Log.e(TAG, "Failed key file writing: ", e);
		}
	}

	public void cancel(View view) {
		setResult(RESULT_CANCELED);
		finish();
	}

	public void deleteFile(View view) {
		if (existingFileName != null) {
			File existingFile = new File(getFilesDir().getPath() + "/" + existingFileName);
			if (!existingFile.exists()) {
				Toast.makeText(this, R.string.file_delete_nexist, Toast.LENGTH_SHORT).show();
				setResult(RESULT_CANCELED);
				finish();
				return;
			}
			if (!existingFile.delete()) {
				Toast.makeText(this, R.string.file_delete_failed, Toast.LENGTH_SHORT).show();
			}
			setResult(RESULT_OK);
			finish();
		} else {
			Toast.makeText(this, R.string.file_delete_err, Toast.LENGTH_SHORT).show();
		}
	}

	private void openFile() {
		File file = new File(getFilesDir().getPath() + "/" + existingFileName);
		try (BufferedSource in = Okio.buffer(Okio.source(file))) {
			fileContents.setText(in.readUtf8());
		} catch (IOException e) {
			Log.e(TAG, "Failed to read key file", e);
			Toast.makeText(this, R.string.file_read_fail, Toast.LENGTH_SHORT).show();
			fileContents.getText().clear();
		}
	}
}
