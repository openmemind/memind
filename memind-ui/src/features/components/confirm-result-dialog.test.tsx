//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

import { describe, expect, it, vi } from 'vitest'
import { render } from 'vitest-browser-react'
import { userEvent } from 'vitest/browser'
import { ConfirmResultDialog } from './confirm-result-dialog'

describe('ConfirmResultDialog', () => {
  it('describes selected-row deletion without filtered-result semantics', async () => {
    const { getByText } = await render(
      <ConfirmResultDialog
        open
        onOpenChange={vi.fn()}
        title='Delete memory items'
        selectedCount={3}
        description='Deletes only the selected row ids.'
        onConfirm={vi.fn()}
      />
    )

    await expect.element(getByText('3 selected rows')).toBeInTheDocument()
    await expect
      .element(getByText('Deletes only the selected row ids.'))
      .toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/filtered results/i)
  })

  it('renders cascading delete warnings', async () => {
    const { getByText } = await render(
      <ConfirmResultDialog
        open
        onOpenChange={vi.fn()}
        title='Delete raw data'
        selectedCount={2}
        description='Deletes selected raw data ids.'
        cascadeWarning='Associated memory items will also be deleted.'
        onConfirm={vi.fn()}
      />
    )

    await expect
      .element(getByText('Associated memory items will also be deleted.'))
      .toBeInTheDocument()
  })

  it('renders result counts returned by the server', async () => {
    const { getByText } = await render(
      <ConfirmResultDialog
        open
        onOpenChange={vi.fn()}
        title='Delete graph entities'
        selectedCount={1}
        description='Deletes selected entities.'
        result={{
          deletedCount: 1,
          affectedMemoryIds: ['alice:agent-a'],
          deletedAliases: 2,
          deletedMentions: 3,
        }}
        onConfirm={vi.fn()}
      />
    )

    await expect.element(getByText('deletedCount: 1')).toBeInTheDocument()
    await expect.element(getByText('affectedMemoryIds: 1')).toBeInTheDocument()
    await expect.element(getByText('deletedAliases: 2')).toBeInTheDocument()
    await expect.element(getByText('deletedMentions: 3')).toBeInTheDocument()
  })

  it('calls onConfirm when the destructive action is clicked', async () => {
    const onConfirm = vi.fn()
    const { getByRole } = await render(
      <ConfirmResultDialog
        open
        onOpenChange={vi.fn()}
        title='Delete selected rows'
        selectedCount={1}
        description='Deletes one selected row.'
        onConfirm={onConfirm}
      />
    )

    await userEvent.click(getByRole('button', { name: 'Delete' }))

    expect(onConfirm).toHaveBeenCalledOnce()
  })
})
