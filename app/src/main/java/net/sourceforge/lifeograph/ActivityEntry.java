/***********************************************************************************

    Copyright (C) 2012-2014 Ahmet Öztürk (aoz_2@yahoo.com)

    This file is part of Lifeograph.

    Lifeograph is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Lifeograph is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Lifeograph.  If not, see <http://www.gnu.org/licenses/>.

 ***********************************************************************************/

package net.sourceforge.lifeograph;

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class ActivityEntry extends Activity
        implements ToDoAction.ToDoObject, DialogInquireText.InquireListener,
        DialogTags.DialogTagsHost
{
    // CHAR FLAGS
    public final int CF_NOTHING = 0x1;
    public final int CF_NEWLINE = 0x2;
    public final int CF_PUNCTUATION_RAW = 0x4;
    public final int CF_SPACE = 0x8; // space that will come eventually
    public final int CF_TAB = 0x10;
    public final int CF_IMMEDIATE = 0x20; // indicates contiguity of chars

    public final int CF_ASTERISK = 0x40; // bold
    public final int CF_UNDERSCORE = 0x80; // italic
    public final int CF_EQUALS = 0x100; // strikethrough
    public final int CF_HASH = 0x400; // highlight
    public final int CF_MARKUP = CF_ASTERISK | CF_UNDERSCORE | CF_EQUALS | CF_HASH;

    public final int CF_SLASH = 0x800;
    public final int CF_ALPHA = 0x1000;
    public final int CF_NUMBER = 0x2000;
    public final int CF_AT = 0x4000; // email
    public final int CF_CHECKBOX = 0x8000;

    public final int CF_DOTYM = 0x10000;
    public final int CF_DOTMD = 0x20000;
    public final int CF_DOTDATE = 0x30000; // DOTMD | DOTYM

    public final int CF_LESS = 0x80000; // tagging
    public final int CF_MORE = 0x100000;
    public final int CF_SBB = 0x200000; // square bracket begin: comments
    public final int CF_SBE = 0x400000; // square bracket end: comments

    public final int CF_IGNORE = 0x40000000;
    public final int CF_EOT = 0x80000000; // End of Text

    public final int CF_PUNCTUATION = CF_PUNCTUATION_RAW | CF_SLASH | CF_DOTDATE | CF_LESS
                                      | CF_MORE | CF_SBB | CF_SBE;
    public final int CF_FORMATCHAR = CF_ASTERISK | CF_UNDERSCORE | CF_EQUALS | CF_HASH | CF_SBB
                                     | CF_SBE;
    public final int CF_NUM_SLSH = CF_NUMBER | CF_SLASH;
    public final int CF_NUM_CKBX = CF_NUMBER | CF_CHECKBOX;
    public final int CF_NONSPACE = CF_PUNCTUATION | CF_MARKUP | CF_ALPHA | CF_NUMBER | CF_AT
                                   | CF_CHECKBOX;

    // CHAR CLASSES
    public final int CC_NONE = 0;
    public final int CC_NUMBER = 0x10;
    public final int CC_ALPHA = 0x20;
    public final int CC_ALPHANUM = 0x30;
    public final int CC_SIGN = 0x40;

    public final int CC_SPACE = 0x100;
    public final int CC_TAB = 0x200;
    public final int CC_NEWLINE = 0x400;
    public final int CC_SEPARATOR = 0x700;
    public final int CC_NOT_SEPARATOR = 0xF8FF;

    public final int CC_ANY = 0xFFFF;

    // PARSER SELECTOR (NEEDED DUE TO LACK OF FUNCTION POINTERS IN JAVA)
    private enum ParSel {
        NULL, TR_SUBH, TR_BOLD, TR_ITLC, TR_STRK, TR_HILT, TR_CMNT, TR_LINK, TR_LNAT,
        TR_LNKD, TR_LIST, TR_IGNR,
        JK_DDMD, JK_DDYM, JK_IGNR, JK_LNHT, JK_LNDT,
        AP_BOLD, AP_CMNT, AP_HEND, AP_HILT, AP_ITLC, AP_LINK, AP_LNDT, AP_LNID, AP_STRK, AP_SUBH
    }

    private enum LinkStatus {
        LS_OK, LS_ENTRY_UNAVAILABLE, LS_INVALID, // separator: to check a valid entry link:
                                                 // linkstatus < LS_INVALID
        LS_CYCLIC, LS_FILE_OK, LS_FILE_INVALID, LS_FILE_UNAVAILABLE, LS_FILE_UNKNOWN
    }

    private ActionBar mActionBar = null;
    //private DrawerLayout mDrawerLayout = null;
    private EditText mEditText = null;
    private Entry m_ptr2entry = null;
    Button mButtonHighlight;

    boolean mFlagSetTextOperation = false;
    boolean mFlagEntryChanged = false;
    boolean mFlagDismissOnExit = false;

    private int mColorMid;
    private int mColorMatchBG;
    private final float sMarkupScale = 0.7f;

    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        setContentView( R.layout.entry );

        Lifeograph.sContext = this;
        Lifeograph.updateScreenSizes();
        Lifeograph.sNumberOfDiaryEditingActivities++;

        mActionBar = getActionBar();
        mActionBar.setDisplayHomeAsUpEnabled( true );

        //mDrawerLayout = ( DrawerLayout ) findViewById( R.id.drawer_layout );

        mEditText = ( EditText ) findViewById( R.id.editTextEntry );
        //mEditText.setMovementMethod( LinkMovementMethod.getInstance() );

        if( Lifeograph.getScreenHeight() >= Lifeograph.MIN_HEIGHT_FOR_NO_EXTRACT_UI )
            mEditText.setImeOptions( EditorInfo.IME_FLAG_NO_EXTRACT_UI );

        // set custom font as the default font may lack the necessary chars such as check marks:
        Typeface font = Typeface.createFromAsset( getAssets(), "OpenSans-Regular.ttf" );
        mEditText.setTypeface( font );

        mEditText.addTextChangedListener( new TextWatcher() {
            public void afterTextChanged( Editable s ) {
            }

            public void beforeTextChanged( CharSequence s, int start, int count, int after ) {
            }

            public void onTextChanged( CharSequence s, int start, int before, int count ) {
                // if( mFlagSetTextOperation == false )
                {
                    // if( start > 0 ) {
                    // m_pos_start = mEditText.getText().toString().indexOf( '\n', start - 1 );
                    // if( m_pos_start == -1 )
                    // m_pos_start = 0;
                    // }
                    //
                    // if( start < m_pos_end ) {
                    // m_pos_end = mEditText.getText().toString().indexOf( '\n', start + count );
                    // if( m_pos_end == -1 )
                    // m_pos_end = mEditText.getText().length();
                    // }
                    mFlagEntryChanged = true;
                }
                parse_text( 0, mEditText.getText().length() );
            }
        } );

        mEditText.setCustomSelectionActionModeCallback( new ActionMode.Callback()
        {

            public boolean onPrepareActionMode( ActionMode mode, Menu menu ) {
                return true;
            }

            public void onDestroyActionMode( ActionMode mode ) {
            }

            public boolean onCreateActionMode( ActionMode mode, Menu menu ) {
                menu.add( Menu.NONE, R.id.visit_link, Menu.FIRST, R.string.go );
                return true;
            }

            public boolean onActionItemClicked( ActionMode mode, MenuItem item ) {
                switch( item.getItemId() ) {
                    case R.id.visit_link:
                        final Editable buffer = mEditText.getEditableText();
                        final ClickableSpan[] link = buffer.getSpans( mEditText.getSelectionStart(),
                                                                      mEditText.getSelectionEnd(),
                                                                      ClickableSpan.class );
                        if( link.length > 0 )
                            link[ 0 ].onClick( mEditText );
                        else
                            Log.i( Lifeograph.TAG, "No link in the selection" );
                        return true;
                }
                return false;
            }
        } );

        Button mButtonBold = ( Button ) findViewById( R.id.buttonBold );
        mButtonBold.setOnClickListener( new View.OnClickListener() {
            public void onClick( View v ) {
                toggleFormat( "*" );
            }
        } );

        Button mButtonItalic = ( Button ) findViewById( R.id.buttonItalic );
        mButtonItalic.setOnClickListener( new View.OnClickListener() {
            public void onClick( View v ) {
                toggleFormat( "_" );
            }
        } );

        Button mButtonStrikethrough = ( Button ) findViewById( R.id.buttonStrikethrough );
        SpannableString spanStringS = new SpannableString( "S" );
        spanStringS.setSpan( new StrikethroughSpan(), 0, 1, 0 );
        mButtonStrikethrough.setText( spanStringS );
        mButtonStrikethrough.setOnClickListener( new View.OnClickListener() {
            public void onClick( View v ) {
                toggleFormat( "=" );
            }
        } );

        mButtonHighlight = ( Button ) findViewById( R.id.buttonHighlight );
        mButtonHighlight.setOnClickListener( new View.OnClickListener() {
            public void onClick( View v ) {
                toggleFormat( "#" );
            }
        } );

        Button mButtonComment = ( Button ) findViewById( R.id.button_comment );
        mButtonComment.setOnClickListener( new View.OnClickListener() {
            public void onClick( View v ) {
                addComment();
            }
        } );

        EditText editTextSearch = ( EditText ) findViewById( ( R.id.editTextSearch ) );
        editTextSearch.setText( Diary.diary.get_search_text() );

        if( Lifeograph.getScreenHeight() >= Lifeograph.MIN_HEIGHT_FOR_NO_EXTRACT_UI )
            editTextSearch.setImeOptions( EditorInfo.IME_FLAG_NO_EXTRACT_UI );

        editTextSearch.addTextChangedListener( new TextWatcher()
        {
            public void afterTextChanged( Editable s ) { }

            public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }

            public void onTextChanged( CharSequence s, int start, int before, int count ) {
                if( mInitialized ) {
                    Diary.diary.set_search_text( s.toString().toLowerCase() );
                    parse_text( 0, mEditText.getText().length() );
                }
                else
                    mInitialized = true;
            }
            private boolean mInitialized = false;
        } );

        show( Diary.diary.m_entries.get( getIntent().getLongExtra( "entry", 0 ) ),
              savedInstanceState == null );

        if( !Lifeograph.getAddFreeNotPurchased() ) {
            LinearLayout container = ( LinearLayout ) findViewById( R.id.main_container );
            View ad = findViewById( R.id.fragmentAd );
            container.removeView( ad );
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if( mFlagDismissOnExit )
            Diary.diary.dismiss_entry( m_ptr2entry );
        else
            sync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.d( Lifeograph.TAG, "ActivityEntry.onDestroy()" );

        Lifeograph.handleDiaryEditingActivityDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d( Lifeograph.TAG, "ActivityEntry.onResume()" );

        Lifeograph.sContext = this;
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu ) {
        super.onCreateOptionsMenu( menu );

        getMenuInflater().inflate( R.menu.menu_entry, menu );

        MenuItem item = menu.findItem( R.id.change_todo_status );
        ToDoAction ToDoAction = ( ToDoAction ) item.getActionProvider();
        ToDoAction.mObject = this;

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu( Menu menu ) {
        super.onPrepareOptionsMenu( menu );

        MenuItem item = menu.findItem( R.id.add_tag );
        item.setTitle( String.valueOf( m_ptr2entry.m_tags.size() ) );

        return true;
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        switch( item.getItemId() ) {
            case android.R.id.home:
                //NavUtils.navigateUpFromSameTask( this );
                finish();
                return true;
            case R.id.add_tag:
                showTagDialog();
                return true;
            case R.id.toggle_favorite:
                toggleFavorite();
                return true;
            case R.id.change_todo_status:
                return false;
            case R.id.edit_date:
                new DialogInquireText( this,
                                       R.string.edit_date,
                                       m_ptr2entry.get_date().format_string(),
                                       R.string.apply,
                                       this ).show();
                return true;
            case R.id.dismiss:
                dismiss();
                return true;
        }

        return super.onOptionsItemSelected( item );
    }

    void updateIcon() {
        if( m_ptr2entry.is_favored() ) {
            Bitmap bmp = BitmapFactory.decodeResource(
                    getResources(), m_ptr2entry.get_icon() )
                            .copy( Bitmap.Config.ARGB_8888, true ); // make the bitmap mutable

            Canvas canvas = new Canvas( bmp );

            Bitmap bmp2 = BitmapFactory.decodeResource( getResources(), R.drawable.ic_favorite );

            Rect rectDest = new Rect(
                    bmp.getWidth()/2, bmp.getHeight()/2,
                    bmp.getWidth()-1, bmp.getHeight()-1 );

            canvas.drawBitmap( bmp2, null, rectDest, null );

            BitmapDrawable bd =  new BitmapDrawable( bmp );
            bd.setTargetDensity( getResources().getDisplayMetrics().densityDpi );

            mActionBar.setIcon( bd );
        }
        else
            mActionBar.setIcon( m_ptr2entry.get_icon() );
    }

    void updateTheme() {
        Theme theme = m_ptr2entry.get_theme();
        mEditText.setBackgroundColor( theme.color_base );
        mEditText.setTextColor( theme.color_text );

        mColorMid = Theme.midtone( theme.color_base, theme.color_text, 0.4f );

        //mColorRegionBG = Theme.midtone( theme.color_base, theme.color_text, 0.9f ); LATER
        mColorMatchBG = Theme.contrast(
                theme.color_base, Theme.s_color_match1, Theme.s_color_match2 );

        //mColorLink = Theme.contrast(
        //theme.color_base, Theme.s_color_link1, Theme.s_color_link2 ); LATER
        //mColorLinkBroken = Theme.contrast(
        //theme.color_base, Theme.s_color_broken1, Theme.s_color_broken2 ); LATER

        mButtonHighlight.setTextColor( theme.color_text );
        SpannableString spanStringH = new SpannableString( "H" );
        spanStringH.setSpan( new BackgroundColorSpan( theme.color_highlight ), 0, 1, 0 );
        mButtonHighlight.setText( spanStringH );
    }

    void sync() {
        if( mFlagEntryChanged ) {
            m_ptr2entry.m_date_changed = ( int ) ( System.currentTimeMillis() / 1000L );
            m_ptr2entry.m_text = mEditText.getText().toString();
            mFlagEntryChanged = false;
        }
    }

    void show( Entry entry, boolean flagParse ) {
        if( entry == null ) {
            Log.e( Lifeograph.TAG, "Empty entry passed to show" );
            return;
        }

        mFlagDismissOnExit = false;
        m_ptr2entry = entry;

        // THEME
        updateTheme();

        // PARSING
        m_pos_start = 0;
        m_pos_end = entry.get_text().length();

        // SETTING TEXT
        // mFlagSetTextOperation = true;
        if( flagParse )
            mEditText.setText( entry.get_text() );
        // mFlagSetTextOperation = false;

        // if( flagParse )
        // parse_text();

        setTitle( entry.get_title_str() );
        mActionBar.setSubtitle( entry.get_info_str() );
        updateIcon();
        invalidateOptionsMenu(); // may be redundant here
    }

    private void toggleFavorite() {
        m_ptr2entry.toggle_favored();
        updateIcon();
    }

    private void dismiss() {
        Lifeograph.showConfirmationPrompt( R.string.entry_dismiss_confirm, R.string.dismiss,
                                           new DialogInterface.OnClickListener()
                                           {
                                               public void onClick( DialogInterface dialog,
                                                                    int id ) {
                                                   mFlagDismissOnExit = true;
                                                   ActivityEntry.this.finish();
                                               }
                                           }, null );
    }

    private void showTagDialog() {
        Dialog dialog = new DialogTags( this, this );
        dialog.show();
    }

    public void setTodoStatus( int s ) {
        m_ptr2entry.set_todo_status( s );
        updateIcon();
    }

    // InquireListener methods
    public void onInquireAction( int id, String text ) {
        switch( id ) {
            case R.string.edit_date:
                Date date = new Date( text );
                if( date.m_date != Date.NOT_SET ) {
                    if( !date.is_ordinal() )
                        date.reset_order_1();
                    Diary.diary.set_entry_date( m_ptr2entry, date );
                    setTitle( m_ptr2entry.get_title_str() );
                    mActionBar.setSubtitle( m_ptr2entry.get_info_str() );
                }
                break;
        }
    }
    public boolean onInquireTextChanged( int id, String s ) {
        switch( id ) {
            case R.string.edit_date:
                long date = Date.parse_string( s );
                return( date > 0 && date != m_ptr2entry.m_date.m_date );
            default:
                return true;
        }
    }

    // TAG DIALOG HOST METHODS =====================================================================
    public void onDialogTagsClose() {
        // update tags label
        invalidateOptionsMenu();

        // update theme
        updateTheme();
        parse_text( 0, mEditText.getText().length() );
    }
    public Entry getEntry() {
        return m_ptr2entry;
    }
    public List< Tag > getTags() {
        return m_ptr2entry.m_tags;
    }
    public void addTag( Tag t ) {
        m_ptr2entry.add_tag( t );
    }
    public void removeTag( Tag t ) {
        m_ptr2entry.remove_tag( t );
    }

    // FORMATTING BUTTONS ==========================================================================
    private void toggleFormat( String markup ) {
        int p_start, p_end;
        if( mEditText.hasSelection() ) {
            int start = -2, end = -1;
            boolean properly_separated = false;

            p_start = mEditText.getSelectionStart();
            p_end = mEditText.getSelectionEnd() - 1;

            int p_firt_nl = mEditText.getText().toString().indexOf( '\n' );
            if( p_firt_nl == -1 ) // there is only heading
                return;
            else if( p_end <= p_firt_nl )
                return;
            else if( p_start > p_firt_nl )
                p_start--; // also evaluate the previous character
            else { // p_start <= p_first_nl
                p_start = p_firt_nl + 1;
                properly_separated = true;
                start = -1;
            }

            for( ;; p_start++ ) {
                AdvancedSpan theSpan = hasSpan( p_start, markup.charAt( 0 ) );
                if( theSpan.getType() == '*' || theSpan.getType() == '_' ||
                    theSpan.getType() == '#' || theSpan.getType() == '=' )
                    return;
                switch( mEditText.getText().charAt( p_start ) ) {
                    case '\n': // selection spreads over more than one
                        if( start >= 0 ) {
                            if( properly_separated ) {
                                mEditText.getText().insert( start, markup );
                                end += 2;
                                p_start += 2;
                                p_end += 2;
                            }
                            else {
                                mEditText.getText().insert( start, " " + markup );
                                end += 3;
                                p_start += 3;
                                p_end += 3;
                            }

                            mEditText.getText().insert( end, markup );

                            properly_separated = true;
                            start = -1;
                            break;
                        }
                        /* else no break */
                    case ' ':
                    case '\t':
                        if( start == -2 ) {
                            properly_separated = true;
                            start = -1;
                        }
                        break;
                        /* else no break */
                    default:
                        if( start == -2 )
                            start = -1;
                        else if( start == -1 )
                            start = p_start;
                        end = p_start;
                        break;
                }
                if( p_start == p_end )
                    break;
            }
            // add markup chars to the beginning and end:
            if( start >= 0 ) {
                if( properly_separated ) {
                    mEditText.getText().insert( start, markup );
                    end += 2;
                }
                else {
                    mEditText.getText().insert( start, " " + markup );
                    end += 3;
                }

                mEditText.getText().insert( end, markup );
                // TODO place_cursor( get_iter_at_offset( end ) );
            }
        }
        else { // no selection case
            p_start = p_end = mEditText.getSelectionStart();
            if( isSpace( p_start ) || p_start == mEditText.length() - 1 ) {
                if( startsLine( p_start ) )
                    return;
                p_start--;
                if( hasSpan( p_start, 'm' ).getType() == 'm' )
                    p_start--;
            }
            else if( hasSpan( p_start, 'm' ).getType() == 'm' ) {
                if( startsLine( p_start ) )
                    return;
                p_start--;
                if( isSpace( p_start ) )
                    p_start += 2;
            }

            Object theSpan = hasSpan( p_start, markup.charAt( 0 ) );

            // if already has the markup remove it
            if( ( ( AdvancedSpan ) theSpan ).getType() == markup.charAt( 0 ) ) {
                p_start = mEditText.getText().getSpanStart( theSpan );
                p_end = mEditText.getText().getSpanEnd( theSpan );
                mEditText.getText().delete( p_start - 1, p_start );
                mEditText.getText().delete( p_end - 1, p_end );
            }
            else if( ( ( AdvancedSpan ) theSpan ).getType() == ' ' ) {
                // find word boundaries:
                while( p_start > 0 ) {
                    char c = mEditText.getText().charAt( p_start );
                    if( c == '\n' || c == ' ' || c == '\t' ) {
                        p_start++;
                        break;
                    }

                    p_start--;
                }
                mEditText.getText().insert( p_start, markup );

                while( p_end < mEditText.getText().length() ) {
                    char c = mEditText.getText().charAt( p_end );
                    if( c == '\n' || c == ' ' || c == '\t' )
                        break;
                    p_end++;
                }
                mEditText.getText().insert( p_end, markup );
                // TODO (if necessary) place_cursor( offset );
            }
        }
    }

    private void addComment() {
        if( mEditText.hasSelection() ) {
            int p_start, p_end;
            p_start = mEditText.getSelectionStart();
            p_end = mEditText.getSelectionEnd() - 1;

            mEditText.getText().insert( p_start, "[[" );
            mEditText.getText().insert( p_end + 2, "]]" );
        }
        else { // no selection case
            int p_start = mEditText.getSelectionStart();
            mEditText.getText().insert( p_start, "[[]]" );
            mEditText.setSelection( p_start + 2 );
        }
    }

    // PARSING VARIABLES ===========================================================================
    private int m_pos_start, m_pos_end, pos_current;
    private int pos_word, /*pos_regular,*/ pos_search, pos_tab;
    private char char_current;
    private int m_cc_last, m_cc_req = CC_ANY;
    private StringBuilder word_last = new StringBuilder();
    private int int_last;
    private int id_last;
    private Date date_last = new Date();
    protected boolean m_flag_hidden_link;

    private java.util.List< AbsChar > m_chars_looked_for = new ArrayList< AbsChar >();
    private ParSel m_applier_nl;

    private class AbsChar  // abstract char
    {
        AbsChar( int f, ParSel a, boolean j ) {
            flags = f;
            applier = a;
            junction = j;
        }
        AbsChar( int f, ParSel a ) {
            flags = f;
            applier = a;
            junction = false;
        }
        int             flags;
        ParSel          applier;
        boolean         junction;
    }

    // SPANS =======================================================================================
    private interface AdvancedSpan
    {
        char getType();
    }
    private class SpanOther implements  AdvancedSpan
    {
        public char getType() {
            return 'O';
        }
    }
    private class SpanNull implements  AdvancedSpan
    {
        public char getType() {
            return ' ';
        }
    }
    private class SpanBold extends StyleSpan implements AdvancedSpan
    {
        public SpanBold() {
            super( Typeface.BOLD );
        }
        public char getType() {
            return '*';
        }
    }
    private class SpanItalic extends StyleSpan implements AdvancedSpan
    {
        public SpanItalic() {
            super( Typeface.ITALIC );
        }
        public char getType() {
            return '_';
        }
    }
    private class SpanHighlight extends BackgroundColorSpan implements AdvancedSpan
    {
        public SpanHighlight() {
            super( m_ptr2entry.get_theme().color_highlight );
        }
        public char getType() {
            return '#';
        }
    }
    private class SpanStrikethrough extends StrikethroughSpan implements AdvancedSpan
    {
        public char getType() {
            return '=';
        }
    }
    private class SpanMarkup extends ForegroundColorSpan implements AdvancedSpan
    {
        public SpanMarkup() {
            super( mColorMid );
        }
        public char getType() {
            return 'm';
        }
    }

    private class LinkDate extends ClickableSpan implements AdvancedSpan
    {
        public LinkDate( long date ) {
            mDate = date;
        }

        @Override
        public void onClick( View widget ) {
            Entry entry = Diary.diary.get_entry( mDate );

            if( entry == null )
                entry = Diary.diary.create_entry( new Date( mDate ), "", false );

            Lifeograph.showElem( entry );
        }

        public char getType() {
            return 'd';
        }

        private final long mDate;
    }
    private class LinkUri extends ClickableSpan implements AdvancedSpan
    {
        public LinkUri( String uri ) {
            mUri = uri;
        }

        @Override
        public void onClick( View widget ) {
            Intent browserIntent = new Intent( Intent.ACTION_VIEW, Uri.parse( mUri ) );
            startActivity( browserIntent );
        }

        public char getType() {
            return 'u';
        }

        private final String mUri;
    }
    private class LinkID extends ClickableSpan implements AdvancedSpan
    {
        public LinkID( int id ) {
            mId = id;
        }

        @Override
        public void onClick( View widget ) {
            DiaryElement elem = Diary.diary.get_element( mId );
            if( elem != null ) {
                if( elem.get_type() != DiaryElement.Type.ENTRY )
                    Log.d( Lifeograph.TAG, "Target is not entry" );
                else
                    Lifeograph.showElem( elem );
            }
        }

        public char getType() {
            return 'i';
        }

        private final int mId;
    }

    private java.util.Vector< Object > mSpans = new java.util.Vector< Object >();

    // PARSING =====================================================================================
    private void reset( int start, int end ) {
        m_pos_start = start;
        m_pos_end = end;
        pos_current = pos_word = /*pos_regular =*/ start;

        // TODO: only remove spans within the parsing boundaries...
        // mEditText.getText().clearSpans(); <-- problematic!!
        for( Object span : mSpans )
            mEditText.getText().removeSpan( span );
        mSpans.clear();

        m_cc_last = CC_NONE;
        m_cc_req = CC_ANY;
        word_last.setLength( 0 );
        int_last = 0;
        date_last.set( 0 );
        id_last = 0;
        m_chars_looked_for.clear();

        if( start == 0 && end > 0 ) {
            // to prevent formatting within title:
            m_chars_looked_for.add( new AbsChar( CF_IGNORE, ParSel.NULL ) );
            m_applier_nl = ParSel.AP_HEND;
            apply_heading();
        }
        else {
            m_chars_looked_for.add( new AbsChar( CF_NOTHING, ParSel.NULL ) );
            m_applier_nl = ParSel.NULL;
        }
    }

    void parse_text( int start, int end ) {
        // everything below should go to Parser when there is one (and there is nothing above
        // as of yet...)
        reset( start, end );

        // this part is different than in c++
        String search_text = Diary.diary.get_search_text();
        boolean flag_search_active = !search_text.isEmpty();
        int i_search = 0;
        int i_search_end = Diary.diary.get_search_text().length() - 1;

        for( ; pos_current < m_pos_end; ++pos_current ) {
            char_current = mEditText.getText().charAt( pos_current );

            if( flag_search_active ) {
                if( search_text.charAt( i_search ) == Character.toLowerCase( char_current ) ) {
                    if( i_search == 0 )
                        pos_search = pos_current;
                    if( i_search == i_search_end ) {
                        apply_match();
                        i_search = 0;
                    }
                    else
                        i_search++;
                }
                else
                    i_search = 0;
            }

            // MARKUP PARSING
            switch( char_current ) {
                case '\n':
                case '\r':
                    process_char( CF_NEWLINE, CF_NUM_CKBX | CF_ALPHA | CF_FORMATCHAR | CF_SLASH
                                              | CF_DOTDATE | CF_MORE | CF_TAB | CF_IGNORE, 0,
                                  ParSel.NULL, CC_NEWLINE );
                    break;
                case ' ':
                    process_char( CF_SPACE, CF_ALPHA | CF_NUMBER | CF_SLASH | CF_DOTDATE
                                            | CF_CHECKBOX, CF_NOTHING, ParSel.TR_SUBH, CC_SPACE );
                    break;
                case '*':
                    process_char( CF_ASTERISK, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE,
                                  CF_NOTHING, ParSel.TR_BOLD, CC_SIGN );
                    break;
                case '_':
                    process_char( CF_UNDERSCORE, CF_NUM_CKBX | CF_SLASH | CF_DOTDATE, CF_NOTHING,
                                  ParSel.TR_ITLC, CC_SIGN );
                    break;
                case '=':
                    process_char( CF_EQUALS, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE,
                                  CF_NOTHING, ParSel.TR_STRK, CC_SIGN );
                    break;
                case '#':
                    process_char( CF_HASH, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE,
                                  CF_NOTHING, ParSel.TR_HILT, CC_SIGN );
                    break;
                case '[':
                    process_char( CF_SBB, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE,
                                  CF_NOTHING, ParSel.TR_CMNT, CC_SIGN );
                    break;
                case ']':
                    process_char( CF_SBE, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE, 0,
                                  ParSel.NULL, CC_SIGN );
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    handle_number(); // calculates numeric value
                    process_char( CF_NUMBER, CF_SLASH | CF_ALPHA | CF_DOTDATE | CF_CHECKBOX,
                                  CF_NOTHING, ParSel.TR_LNKD, CC_NUMBER );
                    break;
                case '.':
                    process_char( CF_DOTDATE, CF_NUM_CKBX | CF_ALPHA | CF_SLASH, CF_NOTHING,
                                  ParSel.TR_IGNR, CC_SIGN );
                    break;

                case '-':
                    process_char( CF_DOTDATE, CF_NUM_CKBX | CF_ALPHA | CF_SLASH, 0, ParSel.NULL,
                                  CC_SIGN );
                    break;
                case '/':
                    process_char( CF_SLASH | CF_DOTDATE, CF_NUM_CKBX | CF_ALPHA, 0, ParSel.NULL,
                                  CC_SIGN );
                    break;
                case ':':
                    process_char( CF_PUNCTUATION_RAW, CF_NUM_CKBX | CF_ALPHA | CF_SLASH
                                                      | CF_DOTDATE, CF_NOTHING, ParSel.TR_LINK,
                                  CC_SIGN );
                    break;
                case '@':
                    process_char( CF_AT, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE,
                                  CF_NOTHING, ParSel.TR_LNAT, CC_SIGN );
                    break;
                case '>':
                    process_char( CF_MORE, CF_NUM_CKBX | CF_ALPHA | CF_SLASH | CF_DOTDATE, 0,
                                  ParSel.NULL, CC_SIGN );
                    break;
                case '\t':
                    process_char( CF_TAB, CF_NUM_SLSH | CF_ALPHA | CF_DOTDATE, CF_NOTHING,
                                  ParSel.TR_LIST, CC_TAB );
                    break;
                // LIST CHARS
                case '☐':
                case '☑':
                case '☒':
                    process_char( CF_CHECKBOX, CF_NUM_SLSH | CF_ALPHA | CF_DOTDATE, 0, ParSel.NULL,
                                  CC_SIGN );
                    break;
                default:
                    process_char( CF_ALPHA, CF_NUM_CKBX | CF_DOTDATE | CF_SLASH, 0, ParSel.NULL,
                                  CC_ALPHA ); // most probably :)
                    break;
            }
        }
        // end of the text -treated like new line
        process_char( CF_NEWLINE, CF_NUM_CKBX | CF_ALPHA | CF_FORMATCHAR | CF_SLASH | CF_DOTDATE
                                  | CF_MORE | CF_TAB, CF_EOT, ParSel.NULL, CC_NEWLINE );
    }

    // PARSING HELPER FUNCTIONS ====================================================================
    private void selectParsingFunc( ParSel ps ) {
        switch( ps ) {
            case TR_SUBH:
                trigger_subheading();
                break;
            case TR_BOLD:
                trigger_bold();
                break;
            case TR_ITLC:
                trigger_italic();
                break;
            case TR_STRK:
                trigger_strikethrough();
                break;
            case TR_HILT:
                trigger_highlight();
                break;
            case TR_CMNT:
                trigger_comment();
                break;
            case TR_IGNR:
                trigger_ignore();
                break;
            case TR_LINK:
                trigger_link();
                break;
            case TR_LNKD:
                trigger_link_date();
                break;
            case TR_LNAT:
                trigger_link_at();
                break;

            case AP_BOLD:
                apply_bold();
                break;
            case AP_CMNT:
                apply_comment();
                break;
            case AP_HILT:
                apply_highlight();
                break;
            case AP_ITLC:
                apply_italic();
                break;
            case AP_LINK:
                apply_link();
                break;
            case AP_LNDT:
                apply_link_date();
                break;
            case AP_LNID:
                apply_link_id();
                break;
            case AP_STRK:
                apply_strikethrough();
                break;
            case AP_SUBH:
                apply_subheading();
                break;

            case JK_DDMD:
                junction_date_dotmd();
                break;
            case JK_DDYM:
                junction_date_dotym();
                break;
            case JK_IGNR:
                junction_ignore();
                break;
            case JK_LNHT:
                junction_link_hidden_tab();
                break;
            case JK_LNDT:
                junction_link_date();
                break;
            default:
                break;
        }
    }

    private void addSpan( Object span, int start, int end, int styles ) {
        mSpans.add( span );
        mEditText.getText().setSpan( span, start, end, styles );
    }

    private boolean isSpace( int offset ) {
        switch( mEditText.getText().charAt( offset ) ) {
            case '\n':
            case '\t':
            case ' ':
                return true;
            default:
                return false;
        }
    }
    private boolean startsLine( int offset ) {
        return offset == 0 || ( mEditText.getText().charAt( offset - 1 ) == '\n' );
    }
    private AdvancedSpan hasSpan( int offset, char type ) {
        Object[] spans = mEditText.getText().getSpans( offset, offset, Object.class );
        boolean hasNoOtherSpan = true;
        for( Object span : spans ) {
            if( span instanceof AdvancedSpan ) {
                if( ( ( AdvancedSpan ) span ).getType() == type ) {
                    return ( AdvancedSpan ) span;
                }
                else
                    hasNoOtherSpan = false;
            }
        }
        return( hasNoOtherSpan ? new SpanNull() : new SpanOther() );
    }

    // PROCESS CHAR ================================================================================
    private void process_char( int satisfies, int breaks, int triggers_on, ParSel triggerer,
                               int cc ) {
        int         cf = m_chars_looked_for.get( 0 ).flags;
        ParSel      applier = m_chars_looked_for.get( 0 ).applier;
        boolean     flag_clear_chars = false;
        boolean     flag_trigger = false;
        boolean     flag_apply = false;

        if( ( satisfies & cf ) != 0 ) {
            if( applier != ParSel.NULL ) {
                if( m_chars_looked_for.get( 0 ).junction )
                    flag_apply = true;
                else if( ( m_cc_last & m_cc_req ) != 0 ) { // not junction = final applier
                    flag_clear_chars = true;
                    flag_apply = true;
                }
            }
            else {
                m_chars_looked_for.remove( 0 );
                if( ( triggers_on & cf ) != 0 )
                    flag_trigger = true;
            }
        }
        else if( ( breaks & cf ) == cf || ( cf & CF_IMMEDIATE ) != 0 ) {
            flag_clear_chars = true;
            if( ( triggers_on & CF_NOTHING ) != 0 )
                flag_trigger = true;
        }
        else if( ( triggers_on & cf ) != 0 ) {
            flag_trigger = true;
        }

        if( ( satisfies & CF_NEWLINE ) != 0 ) {
            flag_clear_chars = true;

            if( m_applier_nl != ParSel.NULL ) {
                selectParsingFunc( m_applier_nl );
                m_applier_nl = ParSel.NULL;
            }
            else if( ( satisfies & CF_EOT ) != 0 && !flag_apply ) {
                m_pos_start = pos_current + 1;
                //apply_regular();
            }
        }

        // DO AS COLLECTED INFORMATION REQUIRES
        if( flag_clear_chars ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_NOTHING, ParSel.NULL ) );
        }
        if( flag_trigger )
            selectParsingFunc( triggerer );
        if( flag_apply )
            selectParsingFunc( applier );

        // UPDATE WORD LAST
        if( ( cc & CC_SEPARATOR ) != 0 )
            word_last.setLength( 0 );
        else {
            if( word_last.length() == 0 )
                pos_word = pos_current;
            word_last.append( char_current );
        }

        // UPDATE CHAR CLASS
        m_cc_last = cc;
    }

    // HANDLE NUMBER ===============================================================================
    private void handle_number() {
        if( m_cc_last == CC_NUMBER ) {
            int_last *= 10;
            int_last += ( char_current - '0' );
        }
        else
            int_last = ( char_current - '0' );
    }

    // PARSING TRIGGERERS ==========================================================================
    private void trigger_subheading() {
        if( m_cc_last == CC_NEWLINE ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_NONSPACE, ParSel.AP_SUBH ) );
            m_cc_req = CC_ANY;
            m_pos_start = pos_current;
        }
    }

    private void trigger_markup( int lf, ParSel ps ) {
        if( ( m_cc_last & CC_NOT_SEPARATOR ) != 0 )
            return;

        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_NONSPACE - lf, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( lf, ps ) );
        m_cc_req = CC_NOT_SEPARATOR;
        m_pos_start = pos_current;
    }

    private void trigger_bold() {
        trigger_markup( CF_ASTERISK, ParSel.AP_BOLD );
    }

    private void trigger_italic() {
        trigger_markup( CF_UNDERSCORE, ParSel.AP_ITLC );
    }

    private void trigger_strikethrough() {
        trigger_markup( CF_EQUALS, ParSel.AP_STRK );
    }

    private void trigger_highlight() {
        trigger_markup( CF_HASH, ParSel.AP_HILT );
    }

    private void trigger_comment() {
        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_SBB|CF_IMMEDIATE, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_SBE, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_SBE|CF_IMMEDIATE, ParSel.AP_CMNT ) );
        m_cc_req = CC_ANY;
        m_pos_start = pos_current;
    }

    private void trigger_link() {
        if( word_last.toString().isEmpty() ) {
            Log.e( Lifeograph.TAG, "trigger_link() called for an empty word_last" );
            return;
            // this is a precaution against some error reports
        }
        m_flag_hidden_link = ( word_last.charAt( 0 ) == '<' );
        if( m_flag_hidden_link )
            word_last.deleteCharAt( 0 );

        m_cc_req = CC_ANY;

        String wl_str = word_last.toString();

        if( wl_str.equals( "http" ) || wl_str.equals( "https" ) || wl_str.equals( "ftp" )
            /*|| wl_str.equals( "file" )*/ ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_SLASH, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_SLASH, ParSel.NULL ) );
//            if( word_last.equals( "file" ) ) {
//                m_chars_looked_for.add( new AbsChar( CF_SLASH, ParSel.NULL ) );
//                m_chars_looked_for.add( new AbsChar( CF_NONSPACE, ParSel.NULL ) );
//            }
//            else
                m_chars_looked_for.add( new AbsChar( CF_ALPHA|CF_NUMBER,
                                        ParSel.NULL ) ); // TODO: add dash
        }
        else if( wl_str.equals( "mailto" ) ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_UNDERSCORE | CF_ALPHA | CF_NUMBER,
                                                 ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_AT, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_ALPHA | CF_NUMBER, ParSel.NULL ) ); // TODO: add dash
        }
        else if( wl_str.equals( "deid" ) && m_flag_hidden_link ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_TAB, ParSel.JK_LNHT, true ) );
            m_chars_looked_for.add( new AbsChar( CF_NONSPACE - CF_MORE, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_MORE, ParSel.AP_LNID ) );
            m_pos_start = pos_word;
            return;
        }
        else
            return;

        if( m_flag_hidden_link ) {
            m_chars_looked_for.add( new AbsChar( CF_TAB, ParSel.JK_LNHT, true ) );
            m_chars_looked_for.add( new AbsChar( CF_NONSPACE - CF_MORE, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_MORE, ParSel.AP_LINK ) );
        }
        else {
            m_chars_looked_for.add( new AbsChar( CF_TAB|CF_NEWLINE|CF_SPACE, ParSel.AP_LINK ) );
        }
        m_pos_start = pos_word;
    }

    private void trigger_link_at() {
        if( ( m_cc_last & CC_SEPARATOR ) != 0 )
            return;

        m_flag_hidden_link = false;
        word_last.insert( 0, "mailto:" );
        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_ALPHA|CF_NUMBER, ParSel.NULL ) ); // TODO: add dash
        m_chars_looked_for.add( new AbsChar( CF_TAB|CF_NEWLINE|CF_SPACE, ParSel.AP_LINK ) );
        m_cc_req = CC_ANY;
        m_pos_start = pos_word;
    }

    private void trigger_link_date() {
        m_cc_req = CC_ANY;
        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_DOTYM, ParSel.JK_DDYM, true ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_DOTMD, ParSel.JK_DDMD, true ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.NULL ) );
        m_chars_looked_for.add( new AbsChar( CF_NUMBER, ParSel.JK_LNDT, true ) );

        m_flag_hidden_link = ( word_last.toString().equals( "<" ) );
        if( m_flag_hidden_link ) {
            m_chars_looked_for.add( new AbsChar( CF_TAB, ParSel.JK_LNHT, true ) );
            m_chars_looked_for.add( new AbsChar( CF_NONSPACE, ParSel.NULL ) );
            m_chars_looked_for.add( new AbsChar( CF_MORE, ParSel.AP_LNDT ) );
            m_pos_start = pos_current - 1;
        }
        else {
            m_pos_start = pos_current;
            // applier is called by junction_link_date() in this case
        }
    }

//    private void trigger_list() {
//
//    }

    private void trigger_ignore() {
        if( m_cc_last == CC_NEWLINE ) {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_TAB|CF_IMMEDIATE, ParSel.JK_IGNR, true ) );
            m_cc_req = CC_ANY;
            m_pos_start = pos_current;
        }
    }

    private void junction_link_date() {
        date_last.set_day( int_last );

        if( date_last.is_valid() ) {
            if( m_flag_hidden_link ) {
                m_chars_looked_for.remove( 0 );
                return;
            }
            else
                apply_link_date();
        }

        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_NOTHING, ParSel.NULL ) );
    }

    private void junction_link_hidden_tab() {
        m_chars_looked_for.remove( 0 );
        pos_tab = pos_current + 1;
        id_last = int_last;     // if not id link assignment is in vain
    }

//    private void junction_list() {
//
//    }

    private void junction_date_dotym() { // dot between year and month
        if( int_last >= Date.YEAR_MIN && int_last <= Date.YEAR_MAX ) {
            date_last.set_year( int_last );
            m_chars_looked_for.remove( 0 );
        }
        else {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_NOTHING, ParSel.NULL ) );
        }
    }

    private void junction_date_dotmd() { // dot between month and day
        if( int_last >= 1 && int_last <= 12
        // two separators must be the same:
            && char_current == word_last.charAt( word_last.length() - 3 ) ) {
            date_last.set_month( int_last );
            m_chars_looked_for.remove( 0 );
        }
        else {
            m_chars_looked_for.clear();
            m_chars_looked_for.add( new AbsChar( CF_NOTHING, ParSel.NULL ) );
        }
    }

    private void junction_ignore() {
        m_chars_looked_for.clear();
        m_chars_looked_for.add( new AbsChar( CF_IGNORE, ParSel.NULL ) );
        apply_ignore();
    }

    // APPLIERS ====================================================================================
    private void apply_heading() {
        int end = 0;
        if( mEditText.getText().charAt( 0 ) != '\n' )
            end = mEditText.getText().toString().indexOf( '\n' );
        if( end == -1 )
            end = mEditText.getText().length();

        addSpan( new TextAppearanceSpan( this, R.style.headingSpan ), 0, end,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new ForegroundColorSpan( m_ptr2entry.get_theme().color_heading ), 0, end, 0 );

        if( !mFlagSetTextOperation ) {
            m_ptr2entry.m_name = mEditText.getText().toString().substring( 0, end );
            // handle_entry_title_changed() will not be used here in Android
        }
    }

    private void apply_subheading() {
        int end = mEditText.getText().toString().indexOf( '\n', m_pos_start );
        if( end == -1 )
            end = mEditText.getText().length();

        addSpan( new TextAppearanceSpan( this, R.style.subheadingSpan ), m_pos_start, end,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new ForegroundColorSpan( m_ptr2entry.get_theme().color_subheading ),
                 m_pos_start, end, 0 );
    }

    private void apply_bold() {
        apply_markup( new SpanBold() );
    }

    private void apply_italic() {
        apply_markup( new SpanItalic() );
    }

    private void apply_strikethrough() {
        apply_markup( new SpanStrikethrough() );
    }

    private void apply_highlight() {
        apply_markup( new SpanHighlight() );
    }

    private void apply_markup( Object span ) {
        addSpan( new RelativeSizeSpan( sMarkupScale ), m_pos_start, m_pos_start + 1,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new SpanMarkup(), m_pos_start, m_pos_start + 1, 0 );

        addSpan( span, m_pos_start + 1, pos_current, 0 );

        addSpan( new RelativeSizeSpan( sMarkupScale ), pos_current, pos_current + 1,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new SpanMarkup(), pos_current, pos_current + 1, 0 );
    }

    private void apply_comment() {
        addSpan( new TextAppearanceSpan( this, R.style.commentSpan ), m_pos_start, pos_current + 1,
                 Spanned.SPAN_INTERMEDIATE );

        addSpan( new ForegroundColorSpan( mColorMid ), m_pos_start, pos_current + 1,
                 Spanned.SPAN_INTERMEDIATE );

        addSpan( new SuperscriptSpan(), m_pos_start, pos_current + 1, 0 );
    }

    private void apply_ignore() {
        // TODO:
//        int end = pos_current;
//        if( mEditText.getText().charAt( end ) != '\n' )
//            end = mEditText.getText().toString().indexOf( '\n' ) - 1;
//        if( end < 0 )
//            end = mEditText.getText().length() - 1;
//        addSpan( new BackgroundColorSpan( colorBG ), m_pos_start, end, 0 );
    }

    private void apply_hidden_link_tags( int end, Object spanLink ) {
        addSpan( new RelativeSizeSpan( sMarkupScale ), m_pos_start, pos_tab,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new SpanMarkup(), m_pos_start, pos_tab, 0 );
        addSpan( new RelativeSizeSpan( sMarkupScale ), pos_current, end,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new SpanMarkup(), pos_current, end, 0 );

        addSpan( spanLink, pos_tab, pos_current, 0 );
    }

    private void apply_link() {
        if( m_flag_hidden_link )
            apply_hidden_link_tags( pos_current + 1, new LinkUri( word_last.toString() ) );
        else
            addSpan( new LinkUri( word_last.toString() ), m_pos_start, pos_current, 0 );
    }

    private void apply_link_id() {
        DiaryElement element = Diary.diary.get_element( id_last );

        if( element != null ) {
            if( element.get_type() == DiaryElement.Type.ENTRY ) {
                apply_hidden_link_tags( pos_current + 1, new LinkID( id_last ) );
                //return;
            }
        }
        // TODO: indicate dead links
    }

    private void apply_link_date() {
        LinkStatus status = LinkStatus.LS_OK;
        Entry ptr2entry = Diary.diary.get_entry( date_last.m_date + 1 ); // + 1 fixes order
        if( ptr2entry == null ) {
            if( Diary.diary.is_read_only() )
                return;
            status = LinkStatus.LS_ENTRY_UNAVAILABLE;
        }
        else if( date_last.get_pure() == m_ptr2entry.m_date.get_pure() )
            status = Diary.diary.get_day_has_multiple_entries( date_last ) ?
                    LinkStatus.LS_OK : LinkStatus.LS_CYCLIC;

        if( status == LinkStatus.LS_OK || status == LinkStatus.LS_ENTRY_UNAVAILABLE ) {
            int end = pos_current + 1;

            if( m_flag_hidden_link )
                apply_hidden_link_tags( end, new LinkDate( date_last.m_date ) );
            else
                addSpan( new LinkDate( date_last.m_date ), m_pos_start, end, 0 );
        }
    }

    private void apply_match() {
        addSpan( new BackgroundColorSpan( mColorMatchBG ), pos_search, pos_current + 1,
                 Spanned.SPAN_INTERMEDIATE );
        addSpan( new ForegroundColorSpan( m_ptr2entry.get_theme().color_base ),
                 pos_search, pos_current + 1, 0 );
    }
}
