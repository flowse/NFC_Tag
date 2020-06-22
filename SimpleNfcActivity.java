@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(saveInstanceState);
  Log.d(TAG, "on create...");
  
  setContentView(R.layout.activity_nfc);
  mText = (EditText)findViewById(R.id.tagdata_view);
  
  processNfcIntent(getIntent());
  
  }
  
