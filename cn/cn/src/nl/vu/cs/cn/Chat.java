package nl.vu.cs.cn;

import java.io.IOException;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.TCP.Socket;
import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.TextView;

public class Chat extends Activity {
	/** Called when the activity is first created. */

	TextView upperTextView, lowerTextView; // Member variable for text view in
											// the layout
	EditText upperInputField, lowerInputField;
	public static int UPPER_IP = 1;
	public static int LOWER_IP = 10;
	public static int LOWER_PORT = 4444;
	Socket lower, upper;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		// Initialize member TextView so we can manipulate it later
		upperTextView = (TextView) findViewById(R.id.upper_chat_window);
		lowerTextView = (TextView) findViewById(R.id.lower_chat_window);

		upperTextView.setMovementMethod(new ScrollingMovementMethod());
		lowerTextView.setMovementMethod(new ScrollingMovementMethod());
		
		upperInputField = (EditText) findViewById(R.id.upper_input_field);
		lowerInputField = (EditText) findViewById(R.id.lower_input_field);

		upperInputField.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN)
						&& (keyCode == KeyEvent.KEYCODE_ENTER)) {

					String upperMsg = upperInputField.getText().toString();
					final byte[] upperWriteBuf = upperMsg.getBytes();
					final byte[] lowerReadBuf = new byte[upperWriteBuf.length];

					Runnable upperTask = new Runnable() {
						@Override
						public void run() {
							upper.write(upperWriteBuf, 0, upperWriteBuf.length);

						}
					};

					Runnable lowerTask = new Runnable() {
						@Override
						public void run() {
							lower.read(lowerReadBuf, 0, lowerReadBuf.length);
						}
					};

					Thread upperThread = new Thread(upperTask);
					upperThread.start();
					lowerTask.run();
					try {
						upperThread.join();
					} catch (InterruptedException e) {
						Log.e("Interrupted", "Error", e);
					}

					String receivedMsg = new String(lowerReadBuf);
					lowerTextView.append(receivedMsg + "\n");
					upperInputField.setText("");
					return true;
				}
				return false;
			}
		});

		lowerInputField.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if ((event.getAction() == KeyEvent.ACTION_DOWN)
						&& (keyCode == KeyEvent.KEYCODE_ENTER)) {

					String lowerMsg = lowerInputField.getText().toString();
					final byte[] lowerWriteBuf = lowerMsg.getBytes();
					final byte[] upperReadBuf = new byte[lowerWriteBuf.length];

					Runnable upperTask = new Runnable() {
						@Override
						public void run() {
							upper.read(upperReadBuf, 0, upperReadBuf.length);
						}
					};

					Runnable lowerTask = new Runnable() {
						@Override
						public void run() {
							lower.write(lowerWriteBuf, 0, lowerWriteBuf.length);
						}
					};

					Thread upperThread = new Thread(upperTask);
					upperThread.start();
					lowerTask.run();
					try {
						upperThread.join();
					} catch (InterruptedException e) {
						Log.e("Interrupted", "Error", e);
					}

					String receivedMsg = new String(upperReadBuf);
					upperTextView.append(receivedMsg + "\n");
					lowerInputField.setText("");
					return true;
				}
				return false;
			}
		});
	}

	public void onPause() {
		super.onPause(); // Always call the superclass method first

		Runnable upperTask = new Runnable() {
			@Override
			public void run() {
				upper.close();
			}
		};

		Runnable lowerTask = new Runnable() {
			@Override
			public void run() {
				lower.close();
			}
		};

		Thread upperThread = new Thread(upperTask);
		upperThread.start();
		lowerTask.run();
		try {
			upperThread.join();
		} catch (InterruptedException e) {
			Log.e("Interrupted", "Error", e);
		}

	}

	@Override
	public void onResume() {
		super.onResume(); // Always call the superclass method first

		try {

			lower = new TCP(UPPER_IP).socket();
			upper = new TCP(LOWER_IP).socket(LOWER_PORT);

			Runnable lowerTask = new Runnable() {
				@Override
				public void run() {
					IpAddress upperAddr = IpAddress.getAddress("192.168.0."
							+ LOWER_IP);
					lower.connect(upperAddr, LOWER_PORT);
				}
			};

			Runnable upperTask = new Runnable() {
				@Override
				public void run() {
					upper.accept();
				}
			};

			Thread upperThread = new Thread(upperTask);
			upperThread.start();
			lowerTask.run();
			upperThread.join();
		} catch (IOException ioe) {
			Log.e("TCP", "Error", ioe);
		} catch (InterruptedException e) {
			Log.e("TCP", "Error", e);
		}
	}

	public void onDestroy() {
		super.onDestroy();

	}
}
