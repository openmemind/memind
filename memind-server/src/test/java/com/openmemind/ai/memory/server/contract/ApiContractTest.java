/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.openmemind.ai.memory.server.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmemind.ai.memory.core.utils.JsonUtils;
import com.openmemind.ai.memory.server.configuration.RequestIdFilter;
import com.openmemind.ai.memory.server.controller.admin.buffer.AdminBufferController;
import com.openmemind.ai.memory.server.controller.admin.dashboard.AdminDashboardController;
import com.openmemind.ai.memory.server.controller.admin.insight.AdminInsightController;
import com.openmemind.ai.memory.server.controller.admin.item.AdminItemController;
import com.openmemind.ai.memory.server.controller.admin.itemgraph.AdminItemGraphController;
import com.openmemind.ai.memory.server.controller.admin.memoryoption.MemoryOptionsController;
import com.openmemind.ai.memory.server.controller.admin.memorythread.AdminMemoryThreadController;
import com.openmemind.ai.memory.server.controller.admin.rawdata.AdminRawDataController;
import com.openmemind.ai.memory.server.controller.openapi.OpenHealthController;
import com.openmemind.ai.memory.server.controller.openapi.OpenMemoryAsyncController;
import com.openmemind.ai.memory.server.controller.openapi.OpenMemoryQueryController;
import com.openmemind.ai.memory.server.controller.openapi.OpenMemorySyncController;
import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.common.ErrorResult;
import com.openmemind.ai.memory.server.domain.common.SuccessResult;
import com.openmemind.ai.memory.server.handler.ApiExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiContractTest {

    @Test
    void controllerHandlersReturnOnlyApiResultEnvelopes() {
        List<String> violations =
                controllerTypes()
                        .flatMap(type -> handlerMethods(type).map(this::describeViolation))
                        .flatMap(Stream::ofNullable)
                        .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void representativeSuccessResponseUsesDataEnvelopeAndRequestIdHeader() throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(new ContractController())
                        .addFilters(new RequestIdFilter())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(JsonUtils.mapper()))
                        .build();

        mockMvc.perform(get("/contract/success").header("X-Request-Id", "rid-contract"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "rid-contract"))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void representativeErrorResponseUsesErrorEnvelopeAndRequestIdHeader() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(new ContractController())
                        .setControllerAdvice(new ApiExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .setMessageConverters(
                                new JacksonJsonHttpMessageConverter(JsonUtils.mapper()))
                        .setValidator(validator)
                        .build();

        mockMvc.perform(
                        post("/contract/validation")
                                .header("X-Request-Id", "rid-error")
                                .contentType(APPLICATION_JSON)
                                .content("{\"userId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "rid-error"))
                .andExpect(jsonPath("$.error.code").value("validation_failed"))
                .andExpect(jsonPath("$.error.message").value("Request validation failed"))
                .andExpect(jsonPath("$.error.details.fieldErrors.userId").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    private Stream<Class<?>> controllerTypes() {
        return Stream.of(
                AdminBufferController.class,
                AdminDashboardController.class,
                AdminInsightController.class,
                AdminItemController.class,
                AdminItemGraphController.class,
                MemoryOptionsController.class,
                AdminMemoryThreadController.class,
                AdminRawDataController.class,
                OpenHealthController.class,
                OpenMemoryAsyncController.class,
                OpenMemoryQueryController.class,
                OpenMemorySyncController.class);
    }

    private Stream<Method> handlerMethods(Class<?> type) {
        return Stream.of(type.getDeclaredMethods()).filter(this::isHandlerMethod);
    }

    private String describeViolation(Method method) {
        return isContractReturnType(method.getGenericReturnType())
                ? null
                : method.getDeclaringClass().getSimpleName()
                        + "#"
                        + method.getName()
                        + " returns "
                        + method.getGenericReturnType().getTypeName();
    }

    private boolean isContractReturnType(Type returnType) {
        if (returnType instanceof Class<?> clazz) {
            return SuccessResult.class.equals(clazz) || ErrorResult.class.equals(clazz);
        }
        if (!(returnType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type rawType = parameterizedType.getRawType();
        if (SuccessResult.class.equals(rawType) || ErrorResult.class.equals(rawType)) {
            return true;
        }
        if (!ResponseEntity.class.equals(rawType)) {
            return false;
        }
        Type bodyType = parameterizedType.getActualTypeArguments()[0];
        if (bodyType instanceof Class<?> bodyClass) {
            return SuccessResult.class.equals(bodyClass)
                    || ErrorResult.class.equals(bodyClass)
                    || ApiResult.class.equals(bodyClass);
        }
        if (bodyType instanceof ParameterizedType bodyParameterizedType) {
            Type bodyRawType = bodyParameterizedType.getRawType();
            return SuccessResult.class.equals(bodyRawType)
                    || ErrorResult.class.equals(bodyRawType)
                    || ApiResult.class.equals(bodyRawType);
        }
        return false;
    }

    private boolean isHandlerMethod(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    @RestController
    private static final class ContractController {

        @GetMapping("/contract/success")
        SuccessResult<StatusResponse> success() {
            return new SuccessResult<>(new StatusResponse("ok"));
        }

        @PostMapping("/contract/validation")
        SuccessResult<Void> validation(@Valid @RequestBody ValidationRequest request) {
            return new SuccessResult<>(null);
        }
    }

    private record StatusResponse(String status) {}

    private record ValidationRequest(@NotBlank String userId) {}
}
