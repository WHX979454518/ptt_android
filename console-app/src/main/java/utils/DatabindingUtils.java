package utils;

import android.databinding.ObservableField;

import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Cancellable;

/**
 * Created by hefei on 2017/5/18.
 */

public class DatabindingUtils {
    public static <T> io.reactivex.Observable<T> toRxObservable(ObservableField<T> observableField){
        return io.reactivex.Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<T> observableEmitter) throws Exception {
                final android.databinding.Observable.OnPropertyChangedCallback callback = new android.databinding.Observable.OnPropertyChangedCallback() {
                    @Override
                    public void onPropertyChanged(android.databinding.Observable sender, int propertyId) {
                        observableEmitter.onNext(observableField.get());
                    }
                };

                observableField.addOnPropertyChangedCallback(callback);

                observableEmitter.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        observableField.removeOnPropertyChangedCallback(callback);
                    }
                });
            }
        });
    }
}
