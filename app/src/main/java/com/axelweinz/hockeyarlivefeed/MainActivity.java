package com.axelweinz.hockeyarlivefeed;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.filament.Box;
import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TwistGestureRecognizer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MainActivity extends AppCompatActivity {

    private ModelRenderable andyRenderable;
    private ModelRenderable hockeyRinkRenderable;
    private ViewRenderable shotInfoRenderable;
    private ArFragment arFragment;
    private HitResult firstHit;
    private static final String TAG = MainActivity.class.getSimpleName();

    private Vector3 rinkPos;

    private Integer modelCount = 0;
    private Vector3 shotPos;
    private String[] teams = {"Detroit", "Maple Leafs", "Sharks", "Boston"};
    private String[] playersArray = {"N. Kronwall", "G. Nyquist", "A. Matthews", "W. Nylander", "E. Karlsson", "J. Thornton",
        "Z. Chára", "B. Marchand"};
    private Map<String, String> players = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        // Assign teams to the players
        int teamCount = 0;
        for (int i=0;i<playersArray.length;i++) {
            players.put(playersArray[i], teams[teamCount]);
            if (i%2 == 1) {
                teamCount += 1;
            }
        }

        ModelRenderable.builder()
                .setSource(this, R.raw.hockeyrink)
                .build()
                .thenAccept(renderable -> hockeyRinkRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> andyRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.e(TAG, "Unable to load Renderable.", throwable);
                            return null;
                        });

//        ViewRenderable.builder()
//                .setView(this, R.layout.shot_info)
//                .build()
//                .thenAccept(renderable -> shotInfoRenderable = renderable);

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    firstHit = hitResult;

                    if (hockeyRinkRenderable == null) {
                        return;
                    }
                    if (modelCount == 1) { // Limit to 1 model
                        shot();
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode hockeyRink = new TransformableNode(arFragment.getTransformationSystem());
                    hockeyRink.setRenderable(hockeyRinkRenderable);

                    // Set correct rotation of model, then disable rotation with twist
                    hockeyRink.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), -90f));
                    hockeyRink.getRotationController().setEnabled(false);
                    hockeyRink.getTranslationController().setEnabled(false);
                    hockeyRink.getScaleController().setMinScale(0.4f);
                    hockeyRink.getScaleController().setMaxScale(2.0f);
                    hockeyRink.setLocalScale(new Vector3(0.8f, 0.8f, 0.8f));
                    rinkPos = hockeyRink.getLocalPosition();
                    Vector3 temp = new Vector3(rinkPos.x + 0, rinkPos.y + (rinkPos.y+0)/10f, rinkPos.z + 0);
                    //hockeyRink.setLocalPosition(temp);

                    hockeyRink.setParent(anchorNode);

                    hockeyRink.select();

                    modelCount += 1;
                });
    }

    /** Called when a shot event occurs */
    public void shot() {

        // Calculate a random position of the shot
        float minZ = -0.50f;
        float maxZ = -0.20f;
        Random r = new Random();
        float rZ = minZ + r.nextFloat() * (maxZ - minZ);
        float minX = -0.35f;
        float maxX = 0.35f;
        r = new Random();
        float rX = minX + r.nextFloat() * (maxX - minX);
        shotPos = new Vector3(rinkPos.x + rX, rinkPos.y + 0, rinkPos.z + rZ);

        // Shooter
        int minPlayer = 0;
        int maxPlayer = playersArray.length - 1;
        int randPlayer = ThreadLocalRandom.current().nextInt(minPlayer, maxPlayer + 1);
        String shooter = playersArray[randPlayer];
        String playerTeam = players.get(shooter);

        // Create a TextView programmatically.
        TextView shotText = new TextView(getApplicationContext());

        // Create a LayoutParams for TextView
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, // Width of TextView
                RelativeLayout.LayoutParams.WRAP_CONTENT); // Height of TextView

        // Apply the layout parameters to TextView widget
        shotText.setLayoutParams(lp);

        // Set text to display in TextView
        shotText.setText(shooter);

        // Set a text color for TextView text
        if (playerTeam == "Detroit") {
            shotText.setTextColor(Color.parseColor("#CE1126"));
        } else if (playerTeam == "Maple Leafs") {
            shotText.setTextColor(Color.parseColor("#003E7E"));
        } else if (playerTeam == "Sharks") {
            shotText.setTextColor(Color.parseColor("#006D75"));
        } else if (playerTeam == "Boston") {
            shotText.setTextColor(Color.parseColor("#FFB81C"));
        } else {
            shotText.setTextColor(Color.parseColor("##000000"));
        }

        ViewRenderable.builder()
                .setView(this, shotText)
                .build()
                .thenAccept(renderable -> shotInfoRenderable = renderable);

        // Create the Anchor.
        Anchor anchor = firstHit.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable andy and add it to the anchor.
        TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
        andy.setRenderable(andyRenderable);

        TransformableNode shotInfo = new TransformableNode((arFragment.getTransformationSystem()));
        shotInfo.setRenderable(shotInfoRenderable);

        andy.getScaleController().setMinScale(0.1f);
        andy.getScaleController().setMaxScale(2.0f);
        andy.setLocalScale(new Vector3(0.2f, 0.2f, 0.2f));
        //Vector3 pos = andy.getLocalPosition();
        //Vector3 temp = new Vector3(pos.x + 0, pos.y + (pos.y+0)/10f, pos.z + 0);
        andy.setLocalPosition(shotPos);

        shotInfo.setLocalPosition(new Vector3(shotPos.x, shotPos.y + 0.1f, shotPos.z));

        andy.setParent(anchorNode);
        shotInfo.setParent(anchorNode);
    }
}
