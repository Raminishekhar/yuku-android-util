package yuku.kirimfidbek;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.entity.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;

import android.content.*;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.*;
import android.provider.*;
import android.util.*;

public class PengirimFidbek {
	public static interface OnSuccessListener {
		void onSuccess(byte[] response);
	}
	
	private final Context context_;
	private final SharedPreferences offlineBuffer_;
	private TangkapSemuaEror tangkapSemuaEror_;
	private ArrayList<String> xisi_;
	private OnSuccessListener onSuccessListener_ = null;
	private boolean lagiKirim_ = false;
	
	public PengirimFidbek(Context context, SharedPreferences offlineBuffer) {
		context_ = context;
		offlineBuffer_ = offlineBuffer;
	}
	
	public void setOnSuccessListener(OnSuccessListener onSuccessListener) {
		onSuccessListener_ = onSuccessListener;
	}
	
	public void activateDefaultUncaughtExceptionHandler() {
		tangkapSemuaEror_ = new TangkapSemuaEror(this);
		tangkapSemuaEror_.aktifkan();
	}

	public void tambah(String isi) {
		muat();

		xisi_.add(isi);
		simpan();
	}

	private synchronized void simpan() {
		if (xisi_ == null) return;

		Editor editor = offlineBuffer_.edit();
		{
			editor.putInt("nfidbek", xisi_.size());

			for (int i = 0; i < xisi_.size(); i++) {
				editor.putString("fidbek/" + i + "/isi", xisi_.get(i));
			}
		}
		editor.commit();
	}

	public synchronized void cobaKirim() {
		muat();

		if (lagiKirim_ || xisi_.size() == 0) return;
		lagiKirim_ = true;

		new Pengirim().start();
	}

	private synchronized void muat() {
		if (xisi_ == null) {
			xisi_ = new ArrayList<String>();
			int nfidbek = offlineBuffer_.getInt("nfidbek", 0);

			for (int i = 0; i < nfidbek; i++) {
				String isi = offlineBuffer_.getString("fidbek/" + i + "/isi", null);
				if (isi != null) {
					xisi_.add(isi);
				}
			}
		}
	}

	private class Pengirim extends Thread {
		@Override
		public void run() {
			boolean berhasil = false;

			Log.d("KirimFidbek", "tred pengirim dimulai. thread id = " + getId());
			
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("http://www.kejut.com/prog/android/fidbek/kirim.php");
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("package_name", context_.getPackageName()));

				int versionCode = 0;
				try {
					versionCode = context_.getPackageManager().getPackageInfo(context_.getPackageName(), 0).versionCode;
				} catch (NameNotFoundException e) {
					Log.w("KirimFidbek", "package get versioncode", e);
				}
				params.add(new BasicNameValuePair("package_versionCode", String.valueOf(versionCode)));

				for (String isi : xisi_) {
					params.add(new BasicNameValuePair("fidbek_isi[]", isi));
				}

				String uniqueId = Settings.Secure.getString(context_.getContentResolver(), Settings.Secure.ANDROID_ID);
				if (uniqueId == null) {
					uniqueId = "null;FINGERPRINT=" + Build.FINGERPRINT;
				}
				params.add(new BasicNameValuePair("uniqueId", uniqueId));

				post.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
				HttpResponse response = client.execute(post);

				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
				
				while (true) {
					byte[] b = new byte[4096];
					int read = content.read(b);

					if (read <= 0) break;
					baos.write(b, 0, read);
				}

				berhasil = true;

				if (onSuccessListener_ != null) {
					onSuccessListener_.onSuccess(baos.toByteArray());
				}
			} catch (IOException e) {
				Log.w("KirimFidbek", "waktu post", e);
			}
			
			if (berhasil) {
				synchronized (PengirimFidbek.this) {
					xisi_.clear();
				}

				simpan();
			}

			Log.d("KirimFidbek", "tred pengirim selesai. berhasil = " + berhasil);

			lagiKirim_ = false;
		}
	}
}