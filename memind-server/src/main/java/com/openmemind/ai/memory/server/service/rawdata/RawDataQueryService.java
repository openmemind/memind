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
package com.openmemind.ai.memory.server.service.rawdata;

import com.openmemind.ai.memory.server.domain.common.PageResponse;
import com.openmemind.ai.memory.server.domain.rawdata.query.RawDataPageQuery;
import com.openmemind.ai.memory.server.domain.rawdata.view.AdminRawDataView;
import com.openmemind.ai.memory.server.mapper.rawdata.AdminRawDataQueryMapper;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class RawDataQueryService {

    private final AdminRawDataQueryMapper rawDataQueryMapper;

    public RawDataQueryService(AdminRawDataQueryMapper rawDataQueryMapper) {
        this.rawDataQueryMapper = rawDataQueryMapper;
    }

    public PageResponse<AdminRawDataView> listRawData(RawDataPageQuery query) {
        return rawDataQueryMapper.page(query);
    }

    public AdminRawDataView getRawData(String rawDataId) {
        return rawDataQueryMapper
                .findByBizId(rawDataId)
                .orElseThrow(() -> new NoSuchElementException("Raw data not found: " + rawDataId));
    }
}
