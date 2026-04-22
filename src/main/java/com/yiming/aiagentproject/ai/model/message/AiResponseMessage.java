package com.yiming.aiagentproject.ai.model.message;

import com.yiming.aiagentproject.ai.model.enums.StreamMessageTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiResponseMessage extends StreamMessage{
    private String data;

    public AiResponseMessage(String data){
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }

}
