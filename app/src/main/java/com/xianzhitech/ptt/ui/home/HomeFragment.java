package com.xianzhitech.ptt.ui.home;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.xianzhitech.ptt.R;
import com.xianzhitech.ptt.ui.base.BaseFragment;
import com.xianzhitech.ptt.ui.util.ResourceUtil;

import org.apache.commons.lang3.ArrayUtils;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.ButterKnife;

/**
 *
 * 显示已经登陆用户的主界面
 *
 * Created by fanchao on 17/12/15.
 */
public class HomeFragment extends BaseFragment<HomeFragment.Callbacks> {

    private enum Tab {
        Conversation(R.string.tab_conversation, R.drawable.ic_chat_bubble, GroupListFragment.class),
        Contacts(R.string.tab_contacts, R.drawable.ic_people, ContactsFragment.class),
        Person(R.string.tab_me, R.drawable.ic_person, PersonFragment.class),
        ;

        public final @StringRes
        int labelRes;
        public final @DrawableRes
        int drawableRes;
        public final Class<? extends Fragment> fragmentClazz;

        Tab(@StringRes int labelRes, @DrawableRes int drawableRes, Class<? extends Fragment> fragmentClazz) {
            this.labelRes = labelRes;
            this.drawableRes = drawableRes;
            this.fragmentClazz = fragmentClazz;
        }
    }


    @Bind(R.id.home_viewPager)
    ViewPager viewPager;

    @Bind(R.id.home_tabContainer)
    ViewGroup tabContainer;

    @BindColor(R.color.primary)
    int selectedTintColor;

    @BindColor(R.color.secondary_text)
    int normalTintColor;

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_home, container, false);
        ButterKnife.bind(this, view);

        final Tab[] values = Tab.values();
        for (final Tab tab : values) {
            final TextView tabView = (TextView) inflater.inflate(R.layout.view_tab, tabContainer, false);
            final Drawable drawable = DrawableCompat.wrap(ResourceUtil.getDrawable(getContext(), tab.drawableRes));
            DrawableCompat.setTint(drawable, normalTintColor);
            tabView.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            tabView.setText(tab.labelRes);
            tabView.setOnClickListener(v -> viewPager.setCurrentItem(ArrayUtils.indexOf(values, tab), true));
            tabContainer.addView(tabView);
        }

        viewPager.setAdapter(new FragmentStatePagerAdapter(getChildFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                return Fragment.instantiate(getContext(), Tab.values()[position].fragmentClazz.getName());
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

        return view;
    }

    private void setTabItemSelected(final int position, final boolean selected) {
        final TextView tabItem = (TextView) tabContainer.getChildAt(position);
        tabItem.setSelected(selected);
        DrawableCompat.setTint(tabItem.getCompoundDrawables()[1], selected ? selectedTintColor : normalTintColor);
    }

    public interface Callbacks {
        void setTitle(CharSequence title);
    }
}
