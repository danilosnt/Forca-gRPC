package com.sisdis.forca.rpc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;

/** Configuração JSON do projeto. */
public final class Json {
    private Json() {
    }

    public static ObjectMapper criar() {
        ObjectMapper json = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Sem isso o Jackson aceitaria {"apelido": 7} convertendo o número em texto.
        for (CoercionInputShape forma : new CoercionInputShape[] {
                CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean}) {
            json.coercionConfigFor(LogicalType.Textual).setCoercion(forma, CoercionAction.Fail);
        }
        return json;
    }
}
