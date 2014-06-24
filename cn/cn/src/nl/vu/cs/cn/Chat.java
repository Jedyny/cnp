package nl.vu.cs.cn;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nl.vu.cs.cn.IP.IpAddress;
import nl.vu.cs.cn.TCP.Socket;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class Chat extends Activity {
	/** Called when the activity is first created. */
	 
	TextView serverTextView, clientTextView; // Member variable for text view in the layout
	EditText serverMessage, clientMessage; 
	Button serverButtonSend, clientButtonSend;
	public static int CLIENT_IP = 1;
	public static int SERVER_IP = 10;
	public static int SERVER_PORT = 4444;
	Socket client, server;
	ExecutorService executor;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		
		// Initialize member TextView so we can manipulate it later
	    serverTextView = (TextView) findViewById(R.id.server_text_message);
	    clientTextView = (TextView) findViewById(R.id.client_text_message);

		serverMessage = (EditText) findViewById(R.id.server_message);
		clientMessage = (EditText) findViewById(R.id.client_message);
		
		serverButtonSend = (Button)findViewById(R.id.server_button);
		clientButtonSend = (Button)findViewById(R.id.client_button);	
		
		
		serverButtonSend.setOnClickListener(new OnClickListener(){
		
			public void onClick(View arg0) {
				
				String serverMsg = serverMessage.getText().toString();
				final byte[] serverWriteBuf = serverMsg.getBytes();
				final byte[] clientReadBuf = new byte[serverWriteBuf.length];
				
				executor.submit(new Runnable() {
					@Override public void run() {
						server.write(serverWriteBuf, 0, serverWriteBuf.length);
					}
				});
				
				executor.submit(new Runnable() {
					@Override public void run() {
						client.read(clientReadBuf, 0, clientReadBuf.length);
						String receivedMsg = new String(clientReadBuf);
						clientTextView.append(receivedMsg+ "\n");
						serverMessage.setText("");
					}
				});
				

		    }
		});
	
		clientButtonSend.setOnClickListener(new OnClickListener(){
			
			public void onClick(View arg0) {
				
				String clientMsg = clientMessage.getText().toString();				
				final byte[] clientWriteBuf = clientMsg.getBytes();
				final byte[] serverReadBuf = new byte[clientWriteBuf.length];
				
				executor.submit(new Runnable() {
					@Override public void run() {
						server.read(serverReadBuf, 0, serverReadBuf.length);
						String receivedMsg = new String(serverReadBuf);
						serverTextView.append(receivedMsg + "\n");
						clientMessage.setText("");
					}
				});
				
				executor.submit(new Runnable() {
					@Override public void run() {
						client.write(clientWriteBuf, 0, clientWriteBuf.length);	
					}
				});
		    }
		});
		
		executor = Executors.newFixedThreadPool(2);
			
}
			 
	
	public void onPause() {
	    super.onPause();  // Always call the superclass method first
	    
	    executor.submit(new Runnable() {
			@Override public void run() {
				server.close();
			}
		});
		
	    executor.submit(new Runnable() {
			@Override public void run() {
				client.close();	
			}
		});
		  
	}
	
	@Override
	public void onResume() {
	    super.onResume();  // Always call the superclass method first
	    
	    try {
			
			client = new TCP(CLIENT_IP).socket();
			server = new TCP(SERVER_IP).socket(SERVER_PORT);
			
			executor.submit(new Runnable() {
				@Override public void run() {
					IpAddress serverAddr = IpAddress.getAddress("192.168.0." + SERVER_IP);
					client.connect(serverAddr, SERVER_PORT);
				}
			});
			
			executor.submit(new Runnable() {
				@Override public void run() {
					server.accept();
				}
			});
			
		} catch (IOException ioe) {
			Log.e("TCP", "Error",ioe );
		}
	}
	
	public void onDestroy(){
		super.onDestroy(); 
		executor.shutdownNow();
	}
}
