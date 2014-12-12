package uk.co.darkerwaters.heartrateanalyser;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class HeartRateView  {
	
	private final View rootView;
	
	private final ImageView heartIcon;
	private final TextView heartText;
	private final ProgressBar heartProgress;

	private final Animation fadeOutAnimation;
	
	public static HeartRateView CreateView(Activity parent) {
		LayoutInflater inflater = (LayoutInflater)parent.getBaseContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	    return new HeartRateView(inflater.inflate(R.layout.actionbar_heartrate, null));
	}

	public HeartRateView(View rootView) {
	    this.rootView =  rootView;
	    
	    this.fadeOutAnimation = AnimationUtils.loadAnimation(this.rootView.getContext(), R.anim.fadeout);
	    
	    this.heartIcon = (ImageView) this.rootView.findViewById(R.id.heart_image);
	    this.heartText = (TextView) this.rootView.findViewById(R.id.heart_text);
	    this.heartProgress = (ProgressBar) this.rootView.findViewById(R.id.heart_progress);
	    
	    // centre the text
	    this.heartText.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
	    noHeartRate();
	}

	public View getView() {
		return this.rootView;
	}
	
	public void updateHeartRate(String rate) {
		this.heartText.setText(rate);
		this.heartText.setVisibility(View.VISIBLE);
		this.heartIcon.setVisibility(View.VISIBLE);
		this.heartIcon.startAnimation(this.fadeOutAnimation); 
		this.heartProgress.setVisibility(View.INVISIBLE);
	}
	
	public void noHeartRate() {
		this.heartText.setVisibility(View.INVISIBLE);
		this.heartProgress.setVisibility(View.INVISIBLE);
	}
	
	public void waitingHeartRate() {
		this.heartText.setVisibility(View.INVISIBLE);
		this.heartProgress.setVisibility(View.VISIBLE);
	}
	
}
