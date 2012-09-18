package com.newsblur.fragment;

import java.io.Serializable;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.newsblur.R;
import com.newsblur.database.FeedProvider;
import com.newsblur.domain.Comment;
import com.newsblur.domain.Story;
import com.newsblur.domain.UserProfile;
import com.newsblur.network.APIManager;
import com.newsblur.util.PrefsUtil;

public class ShareDialogFragment extends DialogFragment {

	private static final String STORY = "story";
	private static final String CALLBACK= "callback";
	private APIManager apiManager;
	private SharedCallbackDialog callback;
	private Story story;
	private UserProfile user;
	private ContentResolver resolver;
	private boolean hasBeenShared = false;
	private Cursor commentCursor;
	private Comment previousComment;

	public static ShareDialogFragment newInstance(final SharedCallbackDialog sharedCallback, final Story story) {
		ShareDialogFragment frag = new ShareDialogFragment();
		Bundle args = new Bundle();
		args.putSerializable(STORY, story);
		args.putSerializable(CALLBACK, sharedCallback);
		frag.setArguments(args);
		return frag;
	}	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setStyle(DialogFragment.STYLE_NO_TITLE, R.style.dialog);
		super.onCreate(savedInstanceState);
		story = (Story) getArguments().getSerializable(STORY);
		callback = (SharedCallbackDialog) getArguments().getSerializable(CALLBACK);
		user = PrefsUtil.getUserDetails(getActivity());

		apiManager = new APIManager(getActivity());
		resolver = getActivity().getContentResolver();

		for (String sharedUserId : story.sharedUserIds) {
			if (TextUtils.equals(user.id, sharedUserId)) {
				hasBeenShared = true;
				break;
			}
		}
		
		if (hasBeenShared) {
			commentCursor = resolver.query(FeedProvider.COMMENTS_URI, null, null, new String[] { story.id, user.id }, null);
			commentCursor.moveToFirst();
			previousComment = Comment.fromCursor(commentCursor);
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
		final String shareString = getResources().getString(R.string.share_newsblur);

		View v = inflater.inflate(R.layout.fragment_dialog, container, false);
		final TextView message = (TextView) v.findViewById(R.id.dialog_message);
		final EditText commentEditText = (EditText) v.findViewById(R.id.dialog_share_comment);
		message.setText(String.format(shareString, story.title));

		if (hasBeenShared) {
			Button shareButton = (Button) v.findViewById(R.id.dialog_button_okay);
			shareButton.setText(R.string.edit);
			commentEditText.setText(previousComment.commentText);
		}

		Button okayButton = (Button) v.findViewById(R.id.dialog_button_okay);
		okayButton.setOnClickListener(new OnClickListener() {
			public void onClick(final View v) {
				final String shareComment = commentEditText.getText().toString();
				v.setEnabled(false);

				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected Boolean doInBackground(Void... arg) {
						return apiManager.shareStory(story.id, story.feedId, shareComment, story.sourceUserId);
					}

					@Override
					protected void onPostExecute(Boolean result) {
						if (result) {
							callback.sharedCallback(shareComment, hasBeenShared);
							Toast.makeText(getActivity(), R.string.shared, Toast.LENGTH_LONG).show();
						} else {
							Toast.makeText(getActivity(), R.string.error_sharing, Toast.LENGTH_LONG).show();
						}
						v.setEnabled(true);
						ShareDialogFragment.this.dismiss();
					};
				}.execute();
			}
		});

		Button cancelButton = (Button) v.findViewById(R.id.dialog_button_cancel);
		cancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				ShareDialogFragment.this.dismiss();
			}
		});

		return v;
	}

	@Override
	public void onDestroy() {
		if (commentCursor != null && !commentCursor.isClosed()) {
			commentCursor.close();
		}
		super.onDestroy();
	}

	public interface SharedCallbackDialog extends Serializable{ 
		public void sharedCallback(String sharedText, boolean alreadyShared);
	}

}
