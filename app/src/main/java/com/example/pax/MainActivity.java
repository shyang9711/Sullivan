package com.example.pax;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements TitleDialog.TitleDialogListener {

    Bitmap image; //Image being used
    private TessBaseAPI mTess; //Tess API reference
    String datapath = "" ; //Language data path

    Button captureBtn; //Photo Button
    Button ocrBtn; //Scan Button
    Button editBtn;
    Button readBtn;
    Button stopBtn;
    Button uploadBtn;

    String selectedLang = "";
    Locale locale;

    TextToSpeech mTTS;

    TextView textData;

    AlertDialog editDialog;
    EditText editText;
    private static final int STORAGE_PERMISSION_CODE = 1;

    Spinner langSpinner; //Language select spinner

    private String imageFilePath; //Image file path
    private Uri mUri;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_IMAGE_CAPTURE = 672;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigation);

        captureBtn = (Button)findViewById(R.id.captureBtn);
        ocrBtn = (Button)findViewById(R.id.ocrBtn);
        editBtn = (Button)findViewById(R.id.editBtn);
        readBtn = (Button) findViewById(R.id.readBtn);
        stopBtn = (Button) findViewById(R.id.stopBtn);
        uploadBtn = (Button) findViewById(R.id.uploadBtn);

        textData = findViewById(R.id.textData);

        editDialog = new AlertDialog.Builder(this).create();
        editText = new EditText(this);

        editDialog.setTitle("Edit the text");
        editDialog.setView(editText);

        checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);

        getIncomingIntent();

        bottomNavigationView.setSelectedItemId(R.id.main);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()){
                    case R.id.main:
                        return true;
                    case R.id.storage:
                        startActivity(new Intent(getApplicationContext(), StorageActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                    case R.id.profile:
                        startActivity(new Intent(getApplicationContext(), ProfileActivity.class));
                        overridePendingTransition(0,0);
                        return true;
                }
                return false;
            }
        });

        langSpinner = (Spinner) findViewById(R.id.langSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.languages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(adapter);

        editDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Save Text", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                textData.setText(editText.getText());
                checkTextData();
            }
        });

        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {

                    langSpinner = findViewById(R.id.langSpinner);

                    Log.d(TAG, "language selected :" + langSpinner.getSelectedItem().toString());

                    int result = mTTS.setLanguage(locale);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED){
                        Log.e("TTS", "Language not supported");
                    }
                } else {
                    Log.e("TTS", "Initialization failed.");
                }
            }
        });

        //TTS Button
        readBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speak();
                stopBtn.setEnabled(true);
            }
        });

        //Stop TTS Button
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTTS.isSpeaking()){
                    mTTS.stop();
                }
            }
        });

        langSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                /**
                 * Tesseract API
                 * 한글 + 영어(함께 추출)
                 * 한글만 추출하거나 영어만 추출하고 싶다면
                 * String lang = "eng"와 같이 작성해도 무관
                 **/
                String lang = langSpinner.getSelectedItem().toString();


                mTess = new TessBaseAPI();
                mTess.init(datapath, lang);
                if (lang.equals("kor")){
                    Log.d(TAG, "onItemSelected: kor");
                    Locale locale = new Locale("ko", "KOR");
                    mTTS.setLanguage(locale);
                } else if (lang.equals("eng")){
                    Log.d(TAG, "onItemSelected: eng");
                    Locale locale = new Locale("en", "USA");
                    mTTS.setLanguage(locale);
                } else{
                    mTTS.setLanguage(locale);

                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        //Tesseract language file directory
        datapath = getFilesDir()+ "/tesseract/";

        //Check if training data has been added
        checkFile(new File(datapath + "tessdata/"), "kor");
        checkFile(new File(datapath + "tessdata/"), "eng");


        //Click Button to open camera
        captureBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                sendTakePhotoIntent();
            }
        });


        // OCR Button
        ocrBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){

                // Extract image to bitmap
                BitmapDrawable d = (BitmapDrawable)((ImageView) findViewById(R.id.imageView)).getDrawable();
                image = d.getBitmap();

                String OCRresult = null;
                mTess.setImage(image);

                //Extract Text
                OCRresult = mTess.getUTF8Text();
                textData.setText(OCRresult);

                //Change Button
                editBtn.setVisibility(View.VISIBLE);

            }
        });

        //Edit Text Button
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                editText.setText(textData.getText());
                editDialog.show();

            }
        });

        //Upload File Button
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
                createFolder();
                openTitleDialog();
            }
        });

        if (savedInstanceState != null){

        }

    }

    public void checkPermission(String permission, int requestCode){
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, requestCode);

        } else {
            Log.d(TAG, "Permission already granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {

            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){


            }
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
            uploadBtn.setEnabled(false);
        }
    }

    private int exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    private Bitmap rotate(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void sendTakePhotoIntent(){

        Log.d(TAG, "sendtakephotointent working");
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            Log.d(TAG, "resolveActivity working");
            try {
                photoFile = createImageFile();
                Log.d(TAG, "createImageFile working");
            } catch (IOException ex) {
                // Error occurred while creating the File
            }

            if (photoFile != null) {
                mUri = FileProvider.getUriForFile(Objects.requireNonNull(getApplicationContext()), BuildConfig.APPLICATION_ID + ".provider", photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mUri);
                imageActivityResultLauncher.launch(takePictureIntent);
            }
        }
    }

    ActivityResultLauncher<Intent> imageActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK){

                        ((ImageView) findViewById(R.id.imageView)).setImageURI(mUri);
                        ExifInterface exif = null;

                        Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
                        try {
                            exif = new ExifInterface(imageFilePath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        int exifOrientation;
                        int exifDegree;

                        if (exif != null) {
                            exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                            exifDegree = exifOrientationToDegrees(exifOrientation);
                        } else {
                            exifDegree = 0;
                        }
                        ((ImageView)findViewById(R.id.imageView)).setImageBitmap(rotate(bitmap, exifDegree));
                        captureBtn.setText("Image");
                        ocrBtn.setVisibility(View.VISIBLE);
                        readBtn.setVisibility(View.VISIBLE);
                        stopBtn.setVisibility(View.VISIBLE);
                        uploadBtn.setVisibility(View.VISIBLE);
                    }
                    }

            }
    );

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "IMAGE_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,      /* prefix */
                ".jpg",         /* suffix */
                storageDir          /* directory */
        );
        imageFilePath = image.getAbsolutePath();
        return image;
    }

    //장치에 파일 복사
    private void copyFiles(String lang) {
        try{
            //파일이 있을 위치
            String filepath = datapath + "/tessdata/"+lang+".traineddata";

            //Access AssetManager
            AssetManager assetManager = getAssets();

            //읽기/쓰기를 위한 열린 바이트 스트림
            InputStream instream = assetManager.open("tesseract/"+lang+".traineddata");
            OutputStream outstream = new FileOutputStream(filepath);

            //filepath에 의해 지정된 위치에 파일 복사
            byte[] buffer = new byte[1024];
            int read;

            while ((read = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, read);
            }
            outstream.flush();
            outstream.close();
            instream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //check file on the device
    private void checkFile(File dir, String lang) {
        //디렉토리가 없으면 디렉토리를 만들고 그후에 파일을 카피
        if(!dir.exists()&& dir.mkdirs()) {
            copyFiles(lang);
        }
        //디렉토리가 있지만 파일이 없으면 파일카피 진행
        if(dir.exists()) {
            String datafilepath = datapath+ "/tessdata/"+lang+".traineddata";
            File datafile = new File(datafilepath);
            if(!datafile.exists()) {
                copyFiles(lang);
            }
        }
    }

    private void speak(){
        String text = textData.getText().toString();

        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void checkTextData(){
        String checkData = textData.getText().toString();

        if (checkData.isEmpty()){
            textData.setText("Choose an image from gallery to perform OCR");
        }
    }

    private void createFolder(){
        File file = new File(Environment.getExternalStorageDirectory(), "PAX");

        if (!file.exists()){
            file.mkdirs();
            if (file.isDirectory()){
                Log.d(TAG, "Directory is created successfully.");
            } else{
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                String mMsg = "Message: failed to create directory"+
                        "\nPath: "+ Environment.getExternalStorageDirectory()+
                        "\nmkdirs: "+ file.mkdirs();
                builder.setMessage(mMsg);
                builder.show();
            }
        }

    }

    public void openTitleDialog(){
        TitleDialog titleDialog = new TitleDialog();
        titleDialog.show(getSupportFragmentManager(), "title dialog");
    }



    @Override
    public void applyTitle(String title) {
        textData = findViewById(R.id.textData);

        String content = textData.getText().toString().trim();

        if (!title.equals("") && !content.equals("")) {
            saveFile(title, content);
        } else
            Toast.makeText(this, "Check your file", Toast.LENGTH_SHORT).show();

    }

    public void saveFile(String title, String content){
        String fileName = title +".txt";

        File file = new File(Environment.getExternalStorageDirectory()+"/PAX", fileName);
        Log.d(TAG, "Filename created");

        try {
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes());
            outputStream.close();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();

        } catch (FileNotFoundException e){
            e.printStackTrace();
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this, "Error while saving", Toast.LENGTH_SHORT).show();
        }
    }

    private void getIncomingIntent(){
        if (getIntent().hasExtra("path")){

            //Receive intent
            Log.d(TAG, "received intent");
            String intentPath = getIntent().getExtras().getString("path");
            Log.d(TAG, "received file path :" + intentPath);
            if (intentPath.contains(".txt")) {
                Log.d(TAG, "text file opened.");
                StringBuilder text = new StringBuilder();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new FileReader(intentPath));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        text.append(line);
                        text.append("\n");
                    }
                    bufferedReader.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, text.toString());
                textData.setText(text.toString());
            } else if (intentPath.contains(".pdf")){

                Log.d(TAG, "pdf file opened.");
                String stringParser;
                try {
                    PdfReader pdfReader = new PdfReader(intentPath);
                    stringParser = PdfTextExtractor.getTextFromPage(pdfReader, 1).trim();
                    pdfReader.close();
                    textData.setText(stringParser);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            editBtn.setVisibility(View.VISIBLE);
            readBtn.setVisibility(View.VISIBLE);
            stopBtn.setVisibility(View.VISIBLE);
            uploadBtn.setVisibility(View.VISIBLE);



        }
    }


    @Override
    protected void onDestroy() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        super.onDestroy();
    }
}
