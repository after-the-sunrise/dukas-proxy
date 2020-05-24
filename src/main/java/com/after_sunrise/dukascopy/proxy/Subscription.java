package com.after_sunrise.dukascopy.proxy;

import com.dukascopy.api.Instrument;
import com.google.gson.annotations.SerializedName;
import org.immutables.gson.Gson;
import org.immutables.value.Value;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Set;

/**
 * @author takanori.takase
 * @version 0.0.0
 */
@Gson.TypeAdapters
@Value.Immutable(singleton = true)
public abstract class Subscription {

    @Nullable
    @SerializedName("id")
    public abstract String getId();

    @Nullable
    @SerializedName("epoch")
    public abstract Instant getEpoch();

    @Nullable
    @SerializedName("success")
    public abstract Boolean getSuccess();

    @Nullable
    @SerializedName("instruments")
    public abstract Set<Instrument> getInstruments();

}
