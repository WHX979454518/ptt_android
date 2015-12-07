package com.podkitsoftware.shoumi.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import com.podkitsoftware.shoumi.R;
import com.podkitsoftware.shoumi.ui.base.BaseActivity;
import com.podkitsoftware.shoumi.ui.home.ContactsFragment;
import com.podkitsoftware.shoumi.ui.home.ConversationFragment;
import com.podkitsoftware.shoumi.ui.home.PersonFragment;
import com.podkitsoftware.shoumi.ui.util.ResourceUtil;

import org.apache.commons.lang3.ArrayUtils;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.ButterKnife;

public class MainActivity extends BaseActivity {

    private enum Tab {
        Conversation(R.string.tab_conversation, R.drawable.ic_chat_bubble, ConversationFragment.class),
        Contacts(R.string.tab_contacts, R.drawable.ic_people, ContactsFragment.class),
        Person(R.string.tab_me, R.drawable.ic_person, PersonFragment.class),
        ;

        public final @StringRes int labelRes;
        public final @DrawableRes int drawableRes;
        public final Class<? extends Fragment> fragmentClazz;

        Tab(@StringRes int labelRes, @DrawableRes int drawableRes, Class<? extends Fragment> fragmentClazz) {
            this.labelRes = labelRes;
            this.drawableRes = drawableRes;
            this.fragmentClazz = fragmentClazz;
        }
    }


    @Bind(R.id.main_viewPager)
    ViewPager viewPager;

    @Bind(R.id.main_toolbar)
    Toolbar toolbar;

    @Bind(R.id.main_tabContainer)
    ViewGroup tabContainer;

    @BindColor(R.color.primary)
    int selectedTintColor;

    @BindColor(R.color.secondary_text)
    int normalTintColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        final LayoutInflater inflater = LayoutInflater.from(this);
        final Tab[] values = Tab.values();
        for (final Tab tab : values) {
            final TextView tabView = (TextView) inflater.inflate(R.layout.view_tab, tabContainer, false);
            final Drawable drawable = DrawableCompat.wrap(ResourceUtil.getDrawable(this, tab.drawableRes));
            DrawableCompat.setTint(drawable, normalTintColor);
            tabView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            tabView.setText(tab.labelRes);
            tabView.setOnClickListener(v -> viewPager.setCurrentItem(ArrayUtils.indexOf(values, tab), true));
            tabContainer.addView(tabView);
        }

        viewPager.setAdapter(new FragmentStatePagerAdapter(getSupportFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                return Fragment.instantiate(MainActivity.this, Tab.values()[position].fragmentClazz.getName());
            }

            @Override
            public int getCount() {
                return Tab.values().length;
            }
        });

        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                for (int i = 0, childCount = tabContainer.getChildCount(); i < childCount; i++) {
                    setTabItemSelected(i, i == position);
                }
            }
        });

        if (savedInstanceState == null) {
            setTabItemSelected(0, true);
        }

        toolbar.setTitle(R.string.app_name);
    }

    private void setTabItemSelected(final int position, final boolean selected) {
        final TextView tabItem = (TextView) tabContainer.getChildAt(position);
        tabItem.setSelected(selected);
        DrawableCompat.setTint(tabItem.getCompoundDrawables()[1], selected ? selectedTintColor : normalTintColor);
    }

}
