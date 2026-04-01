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
package com.openmemind.ai.memory.server.controller.admin.rawdata;

import com.openmemind.ai.memory.server.domain.common.ApiResult;
import com.openmemind.ai.memory.server.domain.common.PageResult;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.request.RawDataDeleteRequest;
import com.openmemind.ai.memory.server.domain.rawdata.response.RawDataDeleteResult;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.service.rawdata.RawDataDeleteService;
import com.openmemind.ai.memory.server.service.rawdata.RawDataQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/admin/v1/raw-data")
public class AdminRawDataController {

    private final RawDataQueryService queryService;
    private final RawDataDeleteService deleteService;

    public AdminRawDataController(
            RawDataQueryService queryService, RawDataDeleteService deleteService) {
        this.queryService = queryService;
        this.deleteService = deleteService;
    }

    @GetMapping
    public ApiResult<PageResult<AdminRawDataView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false) Instant startTimeFrom,
            @RequestParam(required = false) Instant startTimeTo) {
        return ApiResult.success(
                PageResult.from(
                        queryService.listRawData(
                                RawDataPageQuery.of(
                                        pageNo,
                                        pageSize,
                                        userId,
                                        agentId,
                                        startTimeFrom,
                                        startTimeTo))));
    }

    @GetMapping("/{rawDataId}")
    public ApiResult<AdminRawDataView> detail(@PathVariable String rawDataId) {
        return ApiResult.success(queryService.getRawData(rawDataId));
    }

    @DeleteMapping
    public ApiResult<RawDataDeleteResult> delete(@Valid @RequestBody RawDataDeleteRequest request) {
        return ApiResult.success(deleteService.deleteRawData(request.rawDataIds()));
    }
}
