package trady.com.backend.global.kiwoom.watchlist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomWatchlistRequest (
        @JsonProperty("stk_cd") String[] stockCodes
){}
