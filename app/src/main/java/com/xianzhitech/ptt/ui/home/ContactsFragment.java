package com.xianzhitech.ptt.ui.home;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.Bind;
import butterknife.ButterKnife;
import com.xianzhitech.ptt.AppComponent;
import com.xianzhitech.ptt.Broker;
import com.xianzhitech.ptt.R;
import com.xianzhitech.ptt.ui.base.BaseFragment;
import com.xianzhitech.ptt.ui.util.ResourceUtil;
import com.xianzhitech.ptt.ui.util.RxUtil;
import com.xianzhitech.ptt.util.ContactLocaleAwareComparator;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ContactsFragment extends BaseFragment<Void> {

    @Bind(R.id.contacts_list)
    RecyclerView recyclerView;

    @Bind(R.id.contacts_searchBox)
    EditText searchBox;

    int[] accountColors;
    private final Adapter adapter = new Adapter();
    private Broker broker;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        broker = ((AppComponent) getActivity().getApplication()).providesBroker();
        accountColors = getResources().getIntArray(R.array.account_colors);
    }

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        ButterKnife.bind(this, view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        final Drawable searchIcon = DrawableCompat.wrap(ResourceUtil.getDrawable(getContext(), R.drawable.ic_search));
        DrawableCompat.setTint(searchIcon, ResourceUtil.getColor(getContext(), R.color.secondary_text));
        searchBox.setCompoundDrawablesWithIntrinsicBounds(searchIcon, null, null, null);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        Observable.merge(RxUtil.fromTextChanged(searchBox), Observable.just(searchBox.getText().toString()))
                .debounce(500, TimeUnit.MILLISECONDS)
                .flatMap(broker::getContacts)
                .observeOn(AndroidSchedulers.mainThread())
                .compose(this.<List<IContactItem>>bindToLifecycle())
                .subscribe(adapter::setPersons);
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    private class Adapter extends RecyclerView.Adapter<ContactHolder> {
        private final ArrayList<IContactItem> contactItems = new ArrayList<>();

        public void setPersons(final Collection<IContactItem> newPersons) {
            this.contactItems.clear();
            if (newPersons != null) {
                this.contactItems.addAll(newPersons);
                Collections.sort(this.contactItems, new ContactLocaleAwareComparator(Locale.CHINESE));
            }

            notifyDataSetChanged();
        }

        @Override
        public ContactHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
            return new ContactHolder(parent);
        }

        @Override
        public void onBindViewHolder(final ContactHolder holder, final int position) {
            final IContactItem contactItem = contactItems.get(position);
            holder.iconView.setColorFilter(contactItem.getTintColor(getContext()));
            holder.nameView.setText(contactItem.getName());
            holder.itemView.setOnClickListener(v -> {

            });
        }

        @Override
        public int getItemCount() {
            return contactItems.size();
        }
    }

    static class ContactHolder extends RecyclerView.ViewHolder {
        @Bind(R.id.contactItem_icon)
        ImageView iconView;

        @Bind(R.id.contactItem_name)
        TextView nameView;

        public ContactHolder(final ViewGroup container) {
            super(LayoutInflater.from(container.getContext()).inflate(R.layout.view_contact_item, container, false));

            ButterKnife.bind(this, itemView);
        }
    }
}
