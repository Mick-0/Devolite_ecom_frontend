(function () {
  const readCsrf = () => {
    const headerName = document.querySelector('#csrfHeader')?.value;
    const token = document.querySelector('input[name="_csrf"]')?.value;
    if (!headerName || !token) return null;
    return { headerName, token };
  };

  const formatDate = (value) => {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  const initPipelinesModal = () => {
    const pipelinesModal = document.querySelector('[data-pipelines-modal]');
    const openPipelinesBtn = document.querySelector('[data-open-pipelines]');
    if (!pipelinesModal || !openPipelinesBtn) return;

    const pipelineList = pipelinesModal.querySelector('[data-pipeline-list]');
    const pipelineSearch = pipelinesModal.querySelector('[data-pipeline-search]');
    const pipelineSortBy = pipelinesModal.querySelector('[data-pipeline-sort-by]');
    const pipelineSortDir = pipelinesModal.querySelector('[data-pipeline-sort-dir]');
    const pipelineOpen = pipelinesModal.querySelector('[data-pipeline-open]');
    const pipelineStatus = pipelinesModal.querySelector('[data-pipeline-status]');
    const uploadName = pipelinesModal.querySelector('[data-pipeline-upload-name]');
    const uploadFile = pipelinesModal.querySelector('[data-pipeline-upload-file]');
    const uploadBtn = pipelinesModal.querySelector('[data-pipeline-upload-btn]');
    const uploadStatus = pipelinesModal.querySelector('[data-pipeline-upload-status]');
    const closeButtons = Array.from(pipelinesModal.querySelectorAll('[data-modal-close-pipelines]'));

    let cachedPipelines = [];
    let fetchTimer = null;

    const render = (items) => {
      if (!pipelineList) return;
      pipelineList.innerHTML = '';
      if (!items || !items.length) {
        const empty = document.createElement('div');
        empty.className = 'modal-empty';
        empty.textContent = 'Nessuna pipeline trovata.';
        pipelineList.appendChild(empty);
        return;
      }
      items.forEach(item => {
        const el = document.createElement('div');
        el.className = 'modal-item';
        const title = document.createElement('div');
        title.className = 'modal-item__title';
        title.textContent = item.pipelineName || 'Senza nome';
        const meta = document.createElement('div');
        meta.className = 'modal-item__meta';
        const file = document.createElement('span');
        file.textContent = item.originalFilename ? `File: ${item.originalFilename}` : 'File: -';
        const counts = document.createElement('span');
        const active = Number(item.activeRowCount || 0);
        const done = Number(item.doneCount || 0);
        counts.className = 'modal-tag';
        counts.textContent = `${done}/${active} fatte`;
        const time = document.createElement('span');
        time.textContent = formatDate(item.createdAt || item.updatedAt);
        meta.append(file, counts, time);
        el.append(title, meta);
        el.addEventListener('click', () => {
          if (item.pipelineId) {
            window.location.href = `/pipelines/${encodeURIComponent(item.pipelineId)}`;
          }
        });
        pipelineList.appendChild(el);
      });
    };

    const buildQuery = () => {
      const params = new URLSearchParams();
      params.set('limit', '200');
      const q = (pipelineSearch?.value || '').trim();
      const sortBy = pipelineSortBy?.value || 'createdAt';
      const sortDir = pipelineSortDir?.value || 'desc';
      if (q) params.set('q', q);
      params.set('sortBy', sortBy);
      params.set('sortDir', sortDir);
      return params.toString();
    };

    const fetchPipelines = async () => {
      if (!pipelineList) return;
      pipelineList.innerHTML = '<div class="modal-empty">Caricamento...</div>';
      if (pipelineStatus) pipelineStatus.textContent = '';
      try {
        const response = await fetch(`/pipelines/list?${buildQuery()}`, { headers: { 'Accept': 'application/json' } });
        if (!response.ok) throw new Error('load failed');
        cachedPipelines = await response.json();
        render(cachedPipelines);
      } catch (err) {
        cachedPipelines = [];
        pipelineList.innerHTML = '<div class="modal-empty">Errore nel caricamento delle pipeline.</div>';
      }
    };

    const scheduleFetch = () => {
      if (fetchTimer) window.clearTimeout(fetchTimer);
      fetchTimer = window.setTimeout(fetchPipelines, 220);
    };

    const openModal = () => {
      pipelinesModal.classList.add('is-open');
      pipelinesModal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('modal-open');
      if (pipelineSearch) pipelineSearch.value = '';
      if (pipelineSortBy) pipelineSortBy.value = 'createdAt';
      if (pipelineSortDir) pipelineSortDir.value = 'desc';
      if (uploadStatus) uploadStatus.textContent = '';
      fetchPipelines();
      setTimeout(() => pipelineSearch?.focus(), 0);
    };

    const closeModal = () => {
      pipelinesModal.classList.remove('is-open');
      pipelinesModal.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
      if (pipelineStatus) pipelineStatus.textContent = '';
      if (uploadStatus) uploadStatus.textContent = '';
    };

    const openByName = () => {
      const value = (pipelineSearch?.value || '').trim().toLowerCase();
      if (!value && cachedPipelines.length !== 1) {
        if (pipelineStatus) pipelineStatus.textContent = 'Inserisci un criterio di ricerca oppure seleziona una pipeline dalla lista.';
        return;
      }
      const match = cachedPipelines.find(item =>
        (item.pipelineName || '').toLowerCase() === value
        || (item.originalFilename || '').toLowerCase() === value
      );
      const target = match || (cachedPipelines.length === 1 ? cachedPipelines[0] : null);
      if (target?.pipelineId) {
        window.location.href = `/pipelines/${encodeURIComponent(target.pipelineId)}`;
        return;
      }
      if (pipelineStatus) pipelineStatus.textContent = 'Nessuna pipeline corrisponde ai criteri di ricerca.';
    };

    const upload = async () => {
      if (!uploadStatus) return;
      uploadStatus.style.color = '#b54848';
      uploadStatus.textContent = '';
      const name = (uploadName?.value || '').trim();
      const file = uploadFile?.files?.[0];
      if (!name) {
        uploadStatus.textContent = 'Inserisci un nome pipeline.';
        return;
      }
      if (!file) {
        uploadStatus.textContent = 'Seleziona un file CSV.';
        return;
      }
      const csrf = readCsrf();
      if (!csrf) {
        uploadStatus.textContent = 'CSRF mancante. Ricarica la pagina.';
        return;
      }
      const formData = new FormData();
      formData.append('pipelineName', name);
      formData.append('csvFile', file);
      uploadStatus.textContent = 'Import in corso...';
      try {
        const resp = await fetch('/pipelines/upload', {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token, 'Accept': 'application/json' },
          body: formData
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || !payload.ok) {
          uploadStatus.textContent = payload.error || "Errore durante l'import.";
          return;
        }
        uploadStatus.style.color = '#2f6b39';
        uploadStatus.textContent = 'Import completato.';
        if (payload.pipelineId) {
          window.location.href = `/pipelines/${encodeURIComponent(payload.pipelineId)}`;
        } else {
          fetchPipelines();
        }
      } catch (err) {
        uploadStatus.textContent = "Errore durante l'import.";
      }
    };

    openPipelinesBtn.addEventListener('click', openModal);
    closeButtons.forEach(btn => btn.addEventListener('click', closeModal));
    pipelinesModal.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && pipelinesModal.classList.contains('is-open')) closeModal();
    });
    pipelineSearch?.addEventListener('input', scheduleFetch);
    pipelineSearch?.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        openByName();
      }
    });
    pipelineSortBy?.addEventListener('change', fetchPipelines);
    pipelineSortDir?.addEventListener('change', fetchPipelines);
    pipelineOpen?.addEventListener('click', openByName);
    uploadBtn?.addEventListener('click', upload);
  };

  const initPipelinePage = () => {
    const pipelineId = document.body?.dataset?.pipelineId;
    const listEl = document.querySelector('[data-pipeline-row-list]');
    const tableWrapEl = document.querySelector('[data-pipeline-table-wrap]');
    const tableHeadEl = document.querySelector('[data-pipeline-table-head]');
    const tableBodyEl = document.querySelector('[data-pipeline-table-body]');
    const viewButtons = Array.from(document.querySelectorAll('[data-pipeline-view-btn]'));
    if (!pipelineId || !listEl) return;

    const renameInput = document.querySelector('[data-pipeline-rename-name]');
    const renameBtn = document.querySelector('[data-pipeline-rename-save]');
    const renameStatus = document.querySelector('[data-pipeline-rename-status]');
    const setRenameStatus = (msg, isError) => {
      if (!renameStatus) return;
      renameStatus.textContent = msg || '';
      renameStatus.style.color = isError ? '#b54848' : 'var(--muted)';
    };
    if (renameBtn && renameInput) {
      renameBtn.addEventListener('click', async () => {
        const name = String(renameInput.value || '').trim();
        if (!name) {
          setRenameStatus('Inserisci un nome pipeline.', true);
          return;
        }
        const csrf = readCsrf();
        if (!csrf) {
          setRenameStatus('CSRF mancante: ricarica la pagina.', true);
          return;
        }
        renameBtn.disabled = true;
        setRenameStatus('Salvataggio...', false);
        try {
          const res = await fetch(`/pipelines/${encodeURIComponent(pipelineId)}/rename`, {
            method: 'POST',
            headers: { [csrf.headerName]: csrf.token, 'Content-Type': 'application/json', 'Accept': 'application/json' },
            body: JSON.stringify({ pipelineName: name })
          });
          const data = await res.json().catch(() => ({}));
          if (!res.ok || data.ok === false) {
            setRenameStatus(data.error || 'Errore durante la rinomina.', true);
            return;
          }
          setRenameStatus('Nome aggiornato.', false);
        } catch {
          setRenameStatus('Errore durante la rinomina.', true);
        } finally {
          renameBtn.disabled = false;
        }
      });
    }

    const searchEl = document.querySelector('[data-pipeline-row-search]');
    const sortByEl = document.querySelector('[data-pipeline-row-sort-by]');
    const sortDirEl = document.querySelector('[data-pipeline-row-sort-dir]');
    const statusEl = document.querySelector('[data-pipeline-row-status]');
    const columnLabels = {
      project_name: 'Nome progetto',
      company_legal_name: 'Azienda',
      vat_number: 'Partita IVA',
      contact_full_name: 'Contatto',
      contact_email: 'Email',
      contact_phone: 'Telefono',
      crm_temperature: 'Temperatura',
      crm_notes: 'Note CRM',
      crm_sector: 'Settore CRM',
      crm_current_stage: 'Fase CRM',
      crm_cta: 'CTA CRM'
    };
    const temperatureOptions = ['', 'freddo', 'tiepido', 'caldo', 'bollente', 'disqualificato'];
    const stageOptions = ['', 'lead', 'prospect', 'cliente'];
    let currentView = 'cards';

    let fetchTimer = null;

    const setStatus = (msg, isError = true) => {
      if (!statusEl) return;
      statusEl.textContent = msg || '';
      statusEl.style.color = msg ? (isError ? '#b54848' : 'var(--muted)') : '#b54848';
    };

    const buildQuery = () => {
      const params = new URLSearchParams();
      params.set('limit', '500');
      const q = (searchEl?.value || '').trim();
      const sortBy = sortByEl?.value || 'rowNumber';
      const sortDir = sortDirEl?.value || 'asc';
      if (q) params.set('q', q);
      params.set('sortBy', sortBy);
      params.set('sortDir', sortDir);
      return params.toString();
    };

    const openRow = (item) => {
      if (!item) return;
      if (item.doneProjectId) {
        // Open as "Modifica scheda esistente" when already saved.
        window.open(`/intake?projectId=${encodeURIComponent(item.doneProjectId)}`, '_blank', 'noopener');
        return;
      }
      window.open(`/intake?pipelineRowId=${encodeURIComponent(item.rowId)}`, '_blank', 'noopener');
    };

    const deleteRow = async (rowId) => {
      const ok = window.confirm('Vuoi cancellare questa riga dalla pipeline? (soft delete)');
      if (!ok) return;
      const csrf = readCsrf();
      if (!csrf) return;
      try {
        const resp = await fetch(`/pipelines/rows/${encodeURIComponent(rowId)}/delete`, {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token, 'Accept': 'application/json' }
        });
        await resp.json().catch(() => ({}));
        fetchActiveView();
      } catch (err) {
        setStatus('Errore nella cancellazione della riga.');
      }
    };

    const render = (items) => {
      listEl.innerHTML = '';
      if (!items || !items.length) {
        const empty = document.createElement('div');
        empty.className = 'modal-empty';
        empty.textContent = 'Nessuna riga trovata.';
        listEl.appendChild(empty);
        return;
      }
      items.forEach(item => {
        const el = document.createElement('div');
        el.className = 'modal-item';
        const title = document.createElement('div');
        title.className = 'modal-item__title';
        title.textContent = `#${item.rowNumber} - ${item.projectName || 'Senza nome'}`;

        const meta = document.createElement('div');
        meta.className = 'modal-item__meta';
        const company = document.createElement('span');
        company.textContent = item.companyName ? `Azienda: ${item.companyName}` : 'Azienda: -';
        const temp = document.createElement('span');
        temp.className = 'modal-tag';
        temp.textContent = item.crmTemperature ? `Temp: ${item.crmTemperature}` : 'Temp: -';
        const done = document.createElement('span');
        done.className = 'modal-tag';
        done.textContent = item.done ? 'Fatto' : 'Da fare';
        const time = document.createElement('span');
        time.textContent = formatDate(item.updatedAt || item.createdAt);
        meta.append(company, temp, done, time);

        const detail = document.createElement('div');
        detail.className = 'modal-item__detail';
        const contact = document.createElement('span');
        contact.textContent = item.contactFullName ? `Contatto: ${item.contactFullName}` : 'Contatto: -';
        const email = document.createElement('span');
        email.textContent = item.contactEmail ? `Email: ${item.contactEmail}` : 'Email: -';
        const phone = document.createElement('span');
        phone.textContent = item.contactPhone ? `Tel: ${item.contactPhone}` : 'Tel: -';
        detail.append(contact, email, phone);

        const actions = document.createElement('div');
        actions.style.display = 'flex';
        actions.style.justifyContent = 'flex-end';
        actions.style.gap = '8px';

        const openBtn = document.createElement('button');
        openBtn.type = 'button';
        openBtn.className = 'secondary secondary--ghost';
        openBtn.textContent = 'Apri form';
        openBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          if (item.rowId) openRow(item);
        });

        const delBtn = document.createElement('button');
        delBtn.type = 'button';
        delBtn.className = 'secondary';
        delBtn.textContent = 'Cancella';
        delBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          if (item.rowId) deleteRow(item.rowId);
        });

        actions.append(openBtn, delBtn);

        el.append(title, meta, detail, actions);
        el.addEventListener('click', () => {
          if (item.rowId) openRow(item);
        });
        listEl.appendChild(el);
      });
    };

    const fetchRows = async () => {
      listEl.innerHTML = '<div class="modal-empty">Caricamento...</div>';
      setStatus('', false);
      try {
        const resp = await fetch(`/pipelines/${encodeURIComponent(pipelineId)}/rows?${buildQuery()}`, { headers: { 'Accept': 'application/json' } });
        if (!resp.ok) throw new Error('load failed');
        const items = await resp.json();
        render(items);
      } catch (err) {
        listEl.innerHTML = '<div class="modal-empty">Errore nel caricamento delle righe.</div>';
      }
    };

    const isDirtyInput = (input) => String(input.value || '') !== String(input.dataset.originalValue || '');

    const refreshInputState = (input) => {
      input.classList.toggle('is-dirty', isDirtyInput(input));
      if (!isDirtyInput(input)) {
        input.classList.remove('is-error');
      }
    };

    const buildCellInput = (column, value) => {
      const safeValue = value == null ? '' : String(value);
      let input;
      if (column === 'crm_notes') {
        input = document.createElement('textarea');
        input.rows = 3;
      } else if (column === 'crm_temperature' || column === 'crm_current_stage') {
        input = document.createElement('select');
        const options = column === 'crm_temperature' ? temperatureOptions : stageOptions;
        options.forEach((optionValue) => {
          const option = document.createElement('option');
          option.value = optionValue;
          option.textContent = optionValue || '-';
          input.appendChild(option);
        });
      } else {
        input = document.createElement('input');
        input.type = column === 'contact_email' ? 'email' : (column === 'contact_phone' ? 'tel' : 'text');
      }
      input.className = 'pipeline-table__cell-input';
      input.dataset.column = column;
      input.dataset.originalValue = safeValue;
      input.value = safeValue;
      const refresh = () => refreshInputState(input);
      input.addEventListener('input', refresh);
      input.addEventListener('change', refresh);
      refresh();
      return input;
    };

    const collectRowValues = (rowEl) => {
      const values = {};
      rowEl.querySelectorAll('[data-column]').forEach((input) => {
        values[input.dataset.column] = String(input.value || '').trim();
      });
      return values;
    };

    const setRowSaving = (rowEl, saving) => {
      rowEl.querySelectorAll('[data-column]').forEach((input) => {
        input.disabled = saving;
        input.classList.toggle('is-saving', saving);
      });
      rowEl.querySelectorAll('[data-row-action]').forEach((button) => {
        button.disabled = saving;
      });
    };

    const markRowSynced = (rowEl) => {
      rowEl.querySelectorAll('[data-column]').forEach((input) => {
        input.dataset.originalValue = String(input.value || '');
        input.classList.remove('is-saving');
        input.classList.remove('is-error');
        refreshInputState(input);
      });
    };

    const markRowError = (rowEl) => {
      rowEl.querySelectorAll('[data-column]').forEach((input) => {
        if (isDirtyInput(input)) {
          input.classList.add('is-error');
        }
        input.classList.remove('is-saving');
      });
    };

    const saveTableRow = async (item, rowEl) => {
      const csrf = readCsrf();
      const noteEl = rowEl.querySelector('[data-row-note]');
      if (!csrf) {
        if (noteEl) noteEl.textContent = 'CSRF mancante. Ricarica la pagina.';
        markRowError(rowEl);
        return;
      }
      const values = collectRowValues(rowEl);
      setRowSaving(rowEl, true);
      if (noteEl) noteEl.textContent = 'Salvataggio...';
      try {
        const resp = await fetch(`/pipelines/rows/${encodeURIComponent(item.rowId)}/table`, {
          method: 'POST',
          headers: {
            [csrf.headerName]: csrf.token,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          },
          body: JSON.stringify({ values })
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.ok === false) {
          throw new Error(payload.error || 'Errore durante il salvataggio della riga.');
        }
        markRowSynced(rowEl);
        if (noteEl) noteEl.textContent = 'Riga salvata.';
        setStatus('Riga aggiornata.', false);
      } catch (err) {
        markRowError(rowEl);
        if (noteEl) noteEl.textContent = err?.message || 'Errore durante il salvataggio della riga.';
        setStatus(err?.message || 'Errore durante il salvataggio della riga.');
      } finally {
        setRowSaving(rowEl, false);
      }
    };

    const renderTable = (payload) => {
      if (!tableHeadEl || !tableBodyEl) return;
      const columns = Array.isArray(payload?.columns) ? payload.columns : [];
      const rows = Array.isArray(payload?.rows) ? payload.rows : [];

      const headRow = document.createElement('tr');
      ['Riga', 'Stato', 'Aggiornato', ...columns.map((column) => columnLabels[column] || column), 'Azioni'].forEach((label) => {
        const th = document.createElement('th');
        th.textContent = label;
        headRow.appendChild(th);
      });
      tableHeadEl.innerHTML = '';
      tableHeadEl.appendChild(headRow);

      tableBodyEl.innerHTML = '';
      if (!rows.length) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.className = 'modal-empty';
        td.colSpan = columns.length + 4;
        td.textContent = 'Nessuna riga trovata.';
        tr.appendChild(td);
        tableBodyEl.appendChild(tr);
        return;
      }

      rows.forEach((item) => {
        const tr = document.createElement('tr');

        const rowNumberTd = document.createElement('td');
        rowNumberTd.className = 'pipeline-table__meta';
        rowNumberTd.innerHTML = `<strong>#${item.rowNumber || '-'}</strong>`;
        tr.appendChild(rowNumberTd);

        const statusTd = document.createElement('td');
        statusTd.className = 'pipeline-table__meta';
        const badge = document.createElement('span');
        badge.className = 'modal-tag';
        badge.textContent = item.done ? 'Fatto' : 'Da fare';
        statusTd.appendChild(badge);
        tr.appendChild(statusTd);

        const updatedTd = document.createElement('td');
        updatedTd.className = 'pipeline-table__meta';
        updatedTd.textContent = formatDate(item.updatedAt) || '-';
        tr.appendChild(updatedTd);

        columns.forEach((column) => {
          const td = document.createElement('td');
          const input = buildCellInput(column, item.cells?.[column] || '');
          td.appendChild(input);
          tr.appendChild(td);
        });

        const actionsTd = document.createElement('td');
        actionsTd.className = 'pipeline-table__actions';
        const actionsWrap = document.createElement('div');
        actionsWrap.className = 'pipeline-table__actions-wrap';

        const saveBtn = document.createElement('button');
        saveBtn.type = 'button';
        saveBtn.className = 'primary';
        saveBtn.textContent = 'Salva';
        saveBtn.dataset.rowAction = 'save';
        saveBtn.addEventListener('click', () => saveTableRow(item, tr));

        const openBtn = document.createElement('button');
        openBtn.type = 'button';
        openBtn.className = 'secondary';
        openBtn.textContent = 'Apri form';
        openBtn.dataset.rowAction = 'open';
        openBtn.addEventListener('click', () => openRow(item));

        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'secondary';
        deleteBtn.textContent = 'Cancella';
        deleteBtn.dataset.rowAction = 'delete';
        deleteBtn.addEventListener('click', () => deleteRow(item.rowId));

        actionsWrap.append(saveBtn, openBtn, deleteBtn);

        const note = document.createElement('div');
        note.className = 'pipeline-table__row-note';
        note.dataset.rowNote = 'true';
        note.textContent = item.doneProjectId ? 'Già collegata a un progetto salvato.' : '';

        actionsTd.append(actionsWrap, note);
        tr.appendChild(actionsTd);
        tableBodyEl.appendChild(tr);
      });
    };

    const fetchTableRows = async () => {
      if (!tableBodyEl) return;
      tableBodyEl.innerHTML = '<tr><td class="modal-empty" colspan="20">Caricamento...</td></tr>';
      setStatus('', false);
      try {
        const resp = await fetch(`/pipelines/${encodeURIComponent(pipelineId)}/rows/table?${buildQuery()}`, { headers: { 'Accept': 'application/json' } });
        if (!resp.ok) throw new Error('load failed');
        const payload = await resp.json();
        renderTable(payload);
      } catch (err) {
        tableBodyEl.innerHTML = '<tr><td class="modal-empty" colspan="20">Errore nel caricamento tabellare.</td></tr>';
      }
    };

    const fetchActiveView = () => {
      if (currentView === 'table') {
        fetchTableRows();
        return;
      }
      fetchRows();
    };

    const setView = (view) => {
      currentView = view === 'table' ? 'table' : 'cards';
      listEl.hidden = currentView !== 'cards';
      listEl.style.display = currentView === 'cards' ? '' : 'none';
      if (tableWrapEl) {
        tableWrapEl.hidden = currentView !== 'table';
        tableWrapEl.style.display = currentView === 'table' ? '' : 'none';
      }
      viewButtons.forEach((button) => {
        button.classList.toggle('is-active', button.dataset.view === currentView);
      });
      fetchActiveView();
    };

    const scheduleFetch = () => {
      if (fetchTimer) window.clearTimeout(fetchTimer);
      fetchTimer = window.setTimeout(fetchActiveView, 220);
    };

    searchEl?.addEventListener('input', scheduleFetch);
    sortByEl?.addEventListener('change', fetchActiveView);
    sortDirEl?.addEventListener('change', fetchActiveView);
    viewButtons.forEach((button) => {
      button.addEventListener('click', () => setView(button.dataset.view));
    });

    setView(currentView);
  };

  initPipelinesModal();
  initPipelinePage();

  const sections = Array.from(document.querySelectorAll('main section.card[id]'));
  const links = Array.from(document.querySelectorAll('.side nav a[data-section]'));

  if (!sections.length || !links.length) return;

  const byId = new Map(links.map(l => [l.dataset.section, l]));
  const confirmed = new Set(Array.isArray(window.__confirmedSections) ? window.__confirmedSections : []);
  const bodyLockAttr = document.body?.dataset?.enforceSectionLock;
  const enforceSectionLock = String(
    bodyLockAttr !== undefined ? bodyLockAttr : window.__enforceSectionLock
  ).toLowerCase() !== 'false';
  const form = document.querySelector('#intakeForm');
  const projectKindField = form?.querySelector('[name="projectKind"]');
  let progressEls = Array.from(document.querySelectorAll('.form-progress'));
  const side = document.querySelector('.side');
  const sideNav = side?.querySelector('nav');
  const navActive = document.createElement('span');
  navActive.className = 'nav-active';
  if (sideNav && !sideNav.querySelector('.nav-active')) {
    sideNav.appendChild(navActive);
  }
  if (side && progressEls.length) {
    const primary = progressEls[0];
    if (!side.contains(primary)) {
      primary.classList.add('form-progress--side');
      side.insertBefore(primary, sideNav || side.firstChild);
    }
    progressEls.slice(1).forEach(el => el.remove());
  }
  progressEls = Array.from(document.querySelectorAll('.form-progress'));
  const progressTargets = progressEls.map(el => ({
    el,
    valueEl: el.querySelector('[data-progress-value]'),
    fillEl: el.querySelector('[data-progress-fill]')
  }));
  let maxUnlockedIndex = 0;
  let scrollLock = false;

  const existingModal = document.querySelector('[data-existing-modal]');
  const openExistingBtn = document.querySelector('[data-open-existing]');
  const projectList = existingModal?.querySelector('[data-project-list]');
  const projectSearch = existingModal?.querySelector('[data-project-search]');
  const projectSortBy = existingModal?.querySelector('[data-project-sort-by]');
  const projectSortDir = existingModal?.querySelector('[data-project-sort-dir]');
  const projectOpen = existingModal?.querySelector('[data-project-open]');
  const projectStatus = existingModal?.querySelector('[data-project-status]');
  const modalCloseButtons = existingModal ? Array.from(existingModal.querySelectorAll('[data-modal-close]')) : [];
  let cachedProjects = [];
  let projectFetchTimer = null;

  const formatProjectDate = (value) => {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  const projectKindLabels = { vetrina: 'Vetrina', ecommerce: 'E-commerce' };
  const projectStatusLabels = {
    in_discovery: 'In analisi',
    onboarding: 'Avvio',
    in_production: 'In produzione',
    delivered: 'Consegnato',
    archived: 'Archiviato'
  };
  const formatProjectKind = (value) => projectKindLabels[value] || value || '-';
  const formatProjectStatus = (value) => projectStatusLabels[value] || value || '-';
  const getCurrentProjectKind = () => (projectKindField?.value || '').trim();
  const getApplicability = (element) => element?.dataset?.applicable || 'both';
  const isApplicableElement = (element) => {
    const applicability = getApplicability(element);
    if (applicability === 'both') return true;
    const currentKind = getCurrentProjectKind();
    if (!currentKind) return false;
    return applicability === currentKind;
  };
  const getApplicableSections = () => sections.filter(section => isApplicableElement(section));
  const getKindBadge = (applicability) => {
    const currentKind = getCurrentProjectKind();
    if (!currentKind) {
      return 'Seleziona tipo';
    }
    return applicability === 'vetrina' ? 'Solo vetrina' : 'Solo e-commerce';
  };
  const getKindMessage = (applicability) => {
    const currentKind = getCurrentProjectKind();
    if (!currentKind) {
      return 'Seleziona prima il tipo di progetto';
    }
    return applicability === 'vetrina'
      ? 'Disponibile solo per siti vetrina'
      : 'Disponibile solo per progetti e-commerce';
  };

  const renderProjectList = (items) => {
    if (!projectList) return;
    projectList.innerHTML = '';
    if (!items || !items.length) {
      const empty = document.createElement('div');
      empty.className = 'modal-empty';
      empty.textContent = 'Nessun progetto trovato.';
      projectList.appendChild(empty);
      return;
    }
    items.forEach(item => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'modal-item';
      const title = document.createElement('div');
      title.className = 'modal-item__title';
      title.textContent = item.projectName || 'Senza nome';
      const meta = document.createElement('div');
      meta.className = 'modal-item__meta';
      const company = document.createElement('span');
      company.textContent = item.companyName || '-';
      const kind = document.createElement('span');
      kind.className = 'modal-tag';
      kind.textContent = formatProjectKind(item.projectKind);
      const status = document.createElement('span');
      status.className = 'modal-tag';
      status.textContent = formatProjectStatus(item.projectStatus);
      const time = document.createElement('span');
      time.textContent = formatProjectDate(item.updatedAt);
      meta.append(company, kind, status, time);
      const detail = document.createElement('div');
      detail.className = 'modal-item__detail';
      const contact = document.createElement('span');
      contact.textContent = item.contactName ? `Referente: ${item.contactName}` : 'Referente: -';
      const email = document.createElement('span');
      email.textContent = item.contactEmail ? `Email: ${item.contactEmail}` : 'Email: -';
      const vat = document.createElement('span');
      vat.textContent = item.vatNumber ? `P.IVA: ${item.vatNumber}` : 'P.IVA: -';
      const city = document.createElement('span');
      city.textContent = item.city ? `Citta: ${item.city}` : 'Citta: -';
      detail.append(contact, email, vat, city);
      button.append(title, meta, detail);
      button.addEventListener('click', () => {
        if (item.projectId) {
          window.location.href = `/intake?projectId=${encodeURIComponent(item.projectId)}`;
        } else if (item.projectName) {
          window.location.href = `/intake?projectName=${encodeURIComponent(item.projectName)}`;
        }
      });
      projectList.appendChild(button);
    });
  };

  const buildProjectQueryString = () => {
    const params = new URLSearchParams();
    params.set('limit', '100');
    const query = (projectSearch?.value || '').trim();
    const sortBy = projectSortBy?.value || 'updatedAt';
    const sortDir = projectSortDir?.value || 'desc';
    if (query) {
      params.set('q', query);
    }
    params.set('sortBy', sortBy);
    params.set('sortDir', sortDir);
    return params.toString();
  };

  const scheduleProjectFetch = () => {
    if (projectFetchTimer) {
      window.clearTimeout(projectFetchTimer);
    }
    projectFetchTimer = window.setTimeout(() => {
      fetchProjects();
    }, 220);
  };

  const fetchProjects = async () => {
    if (!projectList) return;
    projectList.innerHTML = '<div class="modal-empty">Caricamento...</div>';
    if (projectStatus) projectStatus.textContent = '';
    try {
      const response = await fetch(`/intake/projects?${buildProjectQueryString()}`, {
        headers: { 'Accept': 'application/json' }
      });
      if (!response.ok) {
        throw new Error('load failed');
      }
      cachedProjects = await response.json();
      renderProjectList(cachedProjects);
    } catch (err) {
      cachedProjects = [];
      projectList.innerHTML = '<div class="modal-empty">Errore nel caricamento dei progetti.</div>';
    }
  };

  const openProjectModal = () => {
    if (!existingModal) return;
    existingModal.classList.add('is-open');
    existingModal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('modal-open');
    if (projectSearch) {
      projectSearch.value = '';
      setTimeout(() => projectSearch.focus(), 0);
    }
    if (projectSortBy) {
      projectSortBy.value = 'updatedAt';
    }
    if (projectSortDir) {
      projectSortDir.value = 'desc';
    }
    fetchProjects();
  };

  const closeProjectModal = () => {
    if (!existingModal) return;
    existingModal.classList.remove('is-open');
    existingModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('modal-open');
    if (projectStatus) projectStatus.textContent = '';
  };

  const openProjectByName = () => {
    const value = (projectSearch?.value || '').trim();
    if (!value && !cachedProjects.length) {
      if (projectStatus) projectStatus.textContent = 'Inserisci un criterio di ricerca oppure seleziona un progetto dalla lista.';
      return;
    }
    const lowered = value.toLowerCase();
    const match = cachedProjects.find(item =>
      (item.projectName || '').toLowerCase() === lowered
      || (item.companyName || '').toLowerCase() === lowered
      || (item.vatNumber || '').toLowerCase() === lowered
      || (item.contactEmail || '').toLowerCase() === lowered
    );
    if (match && match.projectId) {
      window.location.href = `/intake?projectId=${encodeURIComponent(match.projectId)}`;
      return;
    }
    if (cachedProjects.length === 1 && cachedProjects[0].projectId) {
      window.location.href = `/intake?projectId=${encodeURIComponent(cachedProjects[0].projectId)}`;
      return;
    }
    if (projectStatus) {
      projectStatus.textContent = cachedProjects.length > 1
        ? 'Sono presenti piu risultati. Selezionane uno dalla lista.'
        : 'Nessun progetto corrisponde ai criteri di ricerca.';
    }
  };

  if (openExistingBtn && existingModal) {
    openExistingBtn.addEventListener('click', openProjectModal);
    modalCloseButtons.forEach(btn => btn.addEventListener('click', closeProjectModal));
    if (projectSearch) {
      projectSearch.addEventListener('input', scheduleProjectFetch);
      projectSearch.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          openProjectByName();
        }
      });
    }
    if (projectSortBy) {
      projectSortBy.addEventListener('change', fetchProjects);
    }
    if (projectSortDir) {
      projectSortDir.addEventListener('change', fetchProjects);
    }
    if (projectOpen) {
      projectOpen.addEventListener('click', openProjectByName);
    }
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && existingModal.classList.contains('is-open')) {
        closeProjectModal();
      }
    });
  }

  const isFilled = (el) => {
    if (el.disabled || el.type === 'hidden') return false;
    if (el.type === 'checkbox' || el.type === 'radio') return el.checked;
    if (el.type === 'file') return el.files && el.files.length > 0;
    return el.value && el.value.trim().length > 0;
  };

  const autoTrackedSectionIds = new Set(['company', 'legal', 'project', 'domain']);

  const isVisibleForProgress = (field) => {
    if (!field) return false;
    const section = field.closest('section.card[id]');
    if (section && !isApplicableElement(section)) return false;
    if (field.closest('[hidden]')) return false;
    return field.getClientRects().length > 0;
  };

  const getTrackedFields = () => {
    if (!form) return [];
    return Array.from(form.querySelectorAll('input, textarea, select'))
      .filter(field => {
        if (field.disabled) return false;
        if (field.type === 'hidden') return false;
        if (field.hasAttribute('data-skip-progress')) return false;
        if (field.type === 'checkbox' || field.type === 'radio') return false;
        if (field.type === 'date' || field.type === 'datetime-local') return false;
        if (['submit', 'reset', 'button', 'image'].includes(field.type)) return false;
        if (!isVisibleForProgress(field)) return false;
        const section = field.closest('section.card[id]');
        const autoTracked = !!(section && autoTrackedSectionIds.has(section.id));
        return autoTracked || field.hasAttribute('data-progress-track');
      });
  };

  const getProgressEligibleFields = () => {
    return getTrackedFields();
  };

  const isCompleted = (field) => {
    if (field.disabled) return false;
    if (field.type === 'checkbox' || field.type === 'radio') return field.checked;
    if (field.type === 'file') return field.files && field.files.length > 0;
    if (field.tagName === 'SELECT') return field.value !== '';
    return field.value != null && field.value.toString().trim().length > 0;
  };

  const applyTodayDates = () => {
    if (!form) return;
    const now = new Date();
    const pad = (value) => String(value).padStart(2, '0');
    const dateValue = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
    const timeValue = `${pad(now.getHours())}:${pad(now.getMinutes())}`;
    const dateInputs = form.querySelectorAll('input[type="date"]');
    dateInputs.forEach(input => {
      if (!input.value) {
        input.value = dateValue;
      }
    });
    const dateTimeInputs = form.querySelectorAll('input[type="datetime-local"]');
    dateTimeInputs.forEach(input => {
      if (!input.value) {
        input.value = `${dateValue}T${timeValue}`;
      }
    });
  };

  const updateFieldCompletion = () => {
    const fields = getProgressEligibleFields();
    fields.forEach(field => {
      const wrapper = field.closest('.field') || field.closest('.check-row') || field.closest('.pill');
      if (!wrapper) return;
      wrapper.classList.toggle('field-complete', isCompleted(field));
    });
  };

  const updateProgress = () => {
    if (!progressTargets.length) return;
    const fields = getTrackedFields();
    const total = fields.length;
    const completed = fields.reduce((acc, field) => acc + (isCompleted(field) ? 1 : 0), 0);
    const percent = total ? Math.round((completed / total) * 100) : 0;
    updateFieldCompletion();
    progressTargets.forEach(({ el, valueEl, fillEl }) => {
      if (valueEl) {
        valueEl.textContent = `${percent}% (${completed}/${total})`;
      }
      if (fillEl) {
        fillEl.style.width = `${percent}%`;
      }
      el.setAttribute('aria-valuenow', String(percent));
    });
  };

  const ensureBadge = (link) => {
    let badge = link.querySelector('.nav-badge');
    if (!badge) {
      badge = document.createElement('span');
      badge.className = 'nav-badge';
      link.appendChild(badge);
    }
    return badge;
  };

  const computeMaxUnlocked = () => {
    const applicableSections = getApplicableSections();
    if (!applicableSections.length) {
      return -1;
    }
    let idx = 0;
    while (idx < applicableSections.length && confirmed.has(applicableSections[idx].id)) {
      idx += 1;
    }
    return Math.min(idx, applicableSections.length - 1);
  };

  const updateNavState = () => {
    const applicableSections = getApplicableSections();
    maxUnlockedIndex = computeMaxUnlocked();
    sections.forEach((section) => {
      const link = byId.get(section.id);
      if (!link) return;
      const applicability = getApplicability(section);
      const typeLocked = !isApplicableElement(section);
      const applicableIndex = applicableSections.indexOf(section);
      const orderLocked = !typeLocked && enforceSectionLock && applicableIndex > maxUnlockedIndex;
      link.classList.toggle('locked', orderLocked);
      link.classList.toggle('kind-locked', typeLocked);
      link.classList.toggle('done', !typeLocked && confirmed.has(section.id));
      link.setAttribute('aria-disabled', (orderLocked || typeLocked) ? 'true' : 'false');
      link.tabIndex = (orderLocked || typeLocked) ? -1 : 0;
      const badge = ensureBadge(link);
      if (typeLocked) {
        badge.textContent = getKindBadge(applicability);
      } else if (confirmed.has(section.id)) {
        badge.textContent = 'Completato';
      } else if (orderLocked) {
        badge.textContent = 'Bloccato';
      } else {
        badge.textContent = 'In attesa';
      }
    });
  };

  const ensureSectionActions = (section) => {
    let actions = section.querySelector('.section-actions');
    if (!actions) {
      actions = document.createElement('div');
      actions.className = 'section-actions';
      const alert = document.createElement('p');
      alert.className = 'section-alert';
      alert.textContent = 'Completa i campi obbligatori della sezione prima di confermare.';
      alert.hidden = true;
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'section-confirm';
      button.textContent = 'Conferma sezione';
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'section-edit';
      edit.textContent = 'Modifica';
      edit.hidden = true;
      actions.append(alert, button, edit);
      section.append(actions);
    }
    return actions;
  };

  const setFieldLock = (section, options) => {
    const { readOnlyLocked, typeLocked } = options;
    const fields = Array.from(section.querySelectorAll('input, textarea, select'));
    fields.forEach(field => {
      if (field.type === 'hidden') return;
      const supportsReadOnly = field.tagName !== 'SELECT' && !['checkbox', 'radio', 'file', 'color'].includes(field.type);
      if (typeLocked) {
        field.disabled = true;
        field.removeAttribute('aria-disabled');
        field.removeAttribute('tabindex');
        if (supportsReadOnly) {
          field.readOnly = false;
        }
      } else {
        field.disabled = false;
        if (supportsReadOnly) {
          field.readOnly = readOnlyLocked;
        }
        if (readOnlyLocked) {
          field.setAttribute('aria-disabled', 'true');
          field.setAttribute('tabindex', '-1');
        } else {
          field.removeAttribute('aria-disabled');
          field.removeAttribute('tabindex');
        }
      }
      const wrapper = field.closest('.field') || field.closest('.check-row') || field.closest('.pill');
      if (wrapper) {
        wrapper.classList.toggle('field-locked', !typeLocked && readOnlyLocked);
        wrapper.classList.toggle('field-kind-locked', typeLocked);
        if (typeLocked) {
          wrapper.classList.remove('field-complete');
        }
      }
    });
  };

  const validateSection = (section) => {
    let valid = true;
    const required = Array.from(section.querySelectorAll('[required]:not([disabled])'));
    required.forEach(field => {
      const filled = isFilled(field);
      const wrapper = field.closest('.field');
      const msg = field.dataset.requiredMessage || 'Compila questo campo.';
      field.setCustomValidity(filled ? '' : msg);
      if (wrapper) {
        wrapper.classList.toggle('has-error', !filled);
        let error = wrapper.querySelector('.error');
        if (!error) {
          error = document.createElement('p');
          error.className = 'error';
          wrapper.appendChild(error);
        }
        if (!filled) {
          error.textContent = msg;
        }
        error.hidden = filled;
      }
      if (!filled && valid) {
        field.focus();
        field.reportValidity();
      }
      valid = valid && filled;
    });
    const alert = section.querySelector('.section-alert');
    if (alert) {
      alert.hidden = valid;
    }
    return valid;
  };

  const lockSections = () => {
    const applicableSections = getApplicableSections();
    maxUnlockedIndex = computeMaxUnlocked();
    sections.forEach(section => {
      const applicability = getApplicability(section);
      const typeLocked = !isApplicableElement(section);
      const applicableIndex = applicableSections.indexOf(section);
      const orderLocked = !typeLocked && enforceSectionLock && applicableIndex > maxUnlockedIndex;
      const isConfirmed = !typeLocked && confirmed.has(section.id);
      section.classList.toggle('section-locked', orderLocked);
      section.classList.toggle('section-kind-locked', typeLocked);
      if (typeLocked) {
        section.setAttribute('data-lock-message', getKindMessage(applicability));
      } else if (orderLocked) {
        section.setAttribute('data-lock-message', 'Conferma la sezione precedente per continuare');
      } else {
        section.removeAttribute('data-lock-message');
      }
      const button = section.querySelector('.section-confirm');
      const edit = section.querySelector('.section-edit');
      if (button) {
        button.disabled = typeLocked || orderLocked || isConfirmed;
        button.textContent = typeLocked ? 'Sezione non applicabile' : (isConfirmed ? 'Sezione confermata' : 'Conferma sezione');
      }
      if (edit) {
        edit.hidden = !isConfirmed || typeLocked;
        edit.disabled = orderLocked || typeLocked;
      }
      setFieldLock(section, { readOnlyLocked: orderLocked || isConfirmed, typeLocked });
    });
    updateNavState();
    updateProgress();
  };

  const getRequestPayload = (sectionId) => {
    const draftIdInput = document.getElementById('draftId');
    const rawDraftId = draftIdInput?.value;
    const rawProjectId = document.getElementById('projectId')?.value;
    return {
      draftId: rawDraftId && rawDraftId.trim().length ? rawDraftId : null,
      projectId: rawProjectId && rawProjectId.trim().length ? rawProjectId : null,
      sectionKey: sectionId,
      projectName: form?.querySelector('input[name="projectName"]')?.value?.trim() || null,
      projectKind: getCurrentProjectKind() || null,
      draftIdInput
    };
  };

  const confirmSection = async (section) => {
    const applicableSections = getApplicableSections();
    const applicableIndex = applicableSections.indexOf(section);
    if (applicableIndex < 0) {
      return;
    }
    if (enforceSectionLock && applicableIndex > maxUnlockedIndex) {
      return;
    }
    if (!validateSection(section)) {
      return;
    }
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
    const csrfHeader = document.getElementById('csrfHeader')?.value || 'X-CSRF-TOKEN';
    const payload = getRequestPayload(section.id);

    try {
      const previousMax = maxUnlockedIndex;
      const response = await fetch('/intake/section/confirm', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify({
          draftId: payload.draftId,
          projectId: payload.projectId,
          sectionKey: payload.sectionKey,
          projectName: payload.projectName,
          projectKind: payload.projectKind
        })
      });
      if (!response.ok) {
        let message = 'Errore nel salvataggio della conferma. Riprova.';
        try {
          const payloadResponse = await response.json();
          if (payloadResponse && payloadResponse.error) {
            message = payloadResponse.error;
          }
        } catch (err) {
          // ignore parsing errors
        }
        throw new Error(message);
      }
      const data = await response.json();
      if (data.draftId && payload.draftIdInput) {
        payload.draftIdInput.value = data.draftId;
      }
      confirmed.clear();
      (data.confirmedSections || []).forEach(s => confirmed.add(s));
      lockSections();
      const nextApplicableSections = getApplicableSections();
      if (maxUnlockedIndex > previousMax && nextApplicableSections[maxUnlockedIndex]) {
        nextApplicableSections[maxUnlockedIndex].scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    } catch (err) {
      const alert = section.querySelector('.section-alert');
      if (alert) {
        alert.textContent = err?.message || 'Errore nel salvataggio della conferma. Riprova.';
        alert.hidden = false;
      }
    }
  };

  const editSection = async (section) => {
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
    const csrfHeader = document.getElementById('csrfHeader')?.value || 'X-CSRF-TOKEN';
    const payload = getRequestPayload(section.id);
    try {
      const response = await fetch('/intake/section/edit', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify({
          draftId: payload.draftId,
          projectId: payload.projectId,
          sectionKey: payload.sectionKey,
          projectKind: payload.projectKind
        })
      });
      if (!response.ok) {
        throw new Error('edit failed');
      }
      const data = await response.json();
      if (data.draftId && payload.draftIdInput) {
        payload.draftIdInput.value = data.draftId;
      }
      confirmed.clear();
      (data.confirmedSections || []).forEach(s => confirmed.add(s));
      lockSections();
    } catch (err) {
      const alert = section.querySelector('.section-alert');
      if (alert) {
        alert.textContent = 'Errore nel passaggio in modifica. Riprova.';
        alert.hidden = false;
      }
    }
  };

  const updateActiveIndicator = (link) => {
    if (!sideNav || !link) return;
    const indicator = sideNav.querySelector('.nav-active');
    if (!indicator) return;
    const navRect = sideNav.getBoundingClientRect();
    const linkRect = link.getBoundingClientRect();
    const top = linkRect.top - navRect.top + 6;
    const height = Math.max(12, linkRect.height - 12);
    indicator.style.top = `${top}px`;
    indicator.style.height = `${height}px`;
  };

  const setActive = (id) => {
    links.forEach(l => {
      const active = l.dataset.section === id;
      l.classList.toggle('active', active);
      if (active) {
        l.setAttribute('aria-current', 'step');
        updateActiveIndicator(l);
      } else {
        l.removeAttribute('aria-current');
      }
    });
  };

  sections.forEach(section => {
    const actions = ensureSectionActions(section);
    const button = actions.querySelector('.section-confirm');
    const edit = actions.querySelector('.section-edit');
    if (button) {
      button.addEventListener('click', () => confirmSection(section));
    }
    if (edit) {
      edit.addEventListener('click', () => editSection(section));
    }
  });

  links.forEach(link => {
    link.addEventListener('click', (event) => {
      if (enforceSectionLock && (link.classList.contains('locked') || link.classList.contains('kind-locked'))) {
        event.preventDefault();
      }
    });
  });

  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      if (entry.target.classList.contains('section-locked')) return;
      setActive(entry.target.id);
    });
  }, { rootMargin: '-35% 0px -60% 0px', threshold: 0 });

  sections.forEach(section => observer.observe(section));

  const enforceScrollLock = () => {
    if (scrollLock) return;
    const locked = sections.find(section => section.classList.contains('section-locked') && section.getBoundingClientRect().top < 120);
    if (!locked) return;
    const applicableSections = getApplicableSections();
    if (!applicableSections[maxUnlockedIndex]) return;
    scrollLock = true;
    applicableSections[maxUnlockedIndex].scrollIntoView({ behavior: 'smooth', block: 'start' });
    setTimeout(() => {
      scrollLock = false;
    }, 250);
  };

  window.addEventListener('scroll', () => {
    requestAnimationFrame(enforceScrollLock);
  });

  updateNavState();
  lockSections();
  setActive(sections[0].id);
  updateActiveIndicator(byId.get(sections[0].id));
  window.addEventListener('resize', () => {
    const active = links.find(l => l.classList.contains('active'));
    if (active) updateActiveIndicator(active);
  });
  if (projectKindField) {
    projectKindField.addEventListener('change', () => {
      lockSections();
    });
  }
  if (form) {
    applyTodayDates();
    form.addEventListener('submit', () => {
      if (!form.checkValidity()) return;
      const fileInputs = Array.from(form.querySelectorAll('input[type="file"]'));
      fileInputs.forEach(input => {
        if (!input.files || input.files.length === 0) {
          input.disabled = true;
        }
      });
    });
  }
  if (form && progressTargets.length) {
    updateProgress();
    form.addEventListener('input', updateProgress);
    form.addEventListener('change', updateProgress);
    setInterval(updateProgress, 1000);
  }
})();

(function () {
  const form = document.querySelector('#intakeForm');
  if (!form) return;

  const countryCode = form.querySelector('input[name="countryCode"]');
  if (countryCode) {
    countryCode.addEventListener('input', () => {
      const cleaned = countryCode.value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 2);
      if (countryCode.value !== cleaned) {
        countryCode.value = cleaned;
      }
    });
  }

  const isValidHex = (value) => /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value || '');
  const normalizeHex = (value) => value.trim().toUpperCase();
  const colorPickers = Array.from(form.querySelectorAll('input[type="color"][data-color-target]'));
  colorPickers.forEach(picker => {
    const targetName = picker.dataset.colorTarget;
    if (!targetName) return;
    const textInput = form.querySelector(`input[name="${targetName}"]`);
    if (!textInput) return;
    const syncFromPicker = () => {
      textInput.value = normalizeHex(picker.value);
      textInput.dispatchEvent(new Event('input', { bubbles: true }));
    };
    const syncFromText = () => {
      const raw = textInput.value || '';
      if (isValidHex(raw)) {
        picker.value = normalizeHex(raw);
      }
    };
    picker.addEventListener('input', syncFromPicker);
    textInput.addEventListener('input', syncFromText);
    syncFromText();
  });

  const total = form.querySelector('input[name="aiSeoCreditsTotal"]');
  const used = form.querySelector('input[name="aiSeoCreditsUsed"]');
  const syncCredits = () => {
    if (!total || !used) return;
    const totalValue = Number.parseInt(total.value, 10);
    if (Number.isNaN(totalValue)) {
      used.removeAttribute('max');
      return;
    }
    used.max = String(totalValue);
    if (used.value && Number(used.value) > totalValue) {
      used.value = String(totalValue);
    }
  };
  if (total && used) {
    total.addEventListener('input', syncCredits);
    used.addEventListener('input', syncCredits);
    syncCredits();
  }

  const syncMinDate = (startInput, endInput) => {
    if (!startInput || !endInput) return;
    if (startInput.value) {
      endInput.min = startInput.value;
      if (endInput.value && endInput.value < startInput.value) {
        endInput.value = startInput.value;
      }
    } else {
      endInput.removeAttribute('min');
    }
  };

  const domainStart = form.querySelector('input[name="domainPurchaseStartedAt"]');
  const domainEnd = form.querySelector('input[name="domainPurchaseCompletedAt"]');
  if (domainStart && domainEnd) {
    const syncDomain = () => syncMinDate(domainStart, domainEnd);
    domainStart.addEventListener('input', syncDomain);
    domainEnd.addEventListener('input', syncDomain);
    syncDomain();
  }

  // Domain section: simplified visibility + suggestions + availability check.
  const hasExisting = form.querySelector('[data-domain-has-existing]');
  const hasCredentials = form.querySelector('[data-domain-has-credentials]');
  const willingNew = form.querySelector('[data-domain-willing-new]');
  const altField = document.querySelector('[data-domain-alt-field]');
  const existingBlocks = Array.from(document.querySelectorAll('[data-domain-existing-fields]'));
  const credentialBlocks = Array.from(document.querySelectorAll('[data-domain-credential-fields]'));
  const newBlocks = Array.from(document.querySelectorAll('[data-domain-new-fields]'));
  const applyDomainVisibility = () => {
    const showExisting = !!(hasExisting && hasExisting.checked);
    existingBlocks.forEach(el => (el.style.display = showExisting ? '' : 'none'));
    newBlocks.forEach(el => (el.style.display = showExisting ? 'none' : ''));

    const showCred = showExisting && !!(hasCredentials && hasCredentials.checked);
    credentialBlocks.forEach(el => (el.style.display = showCred ? '' : 'none'));

    const showAlt = !!(willingNew && willingNew.checked);
    if (altField) altField.style.display = showAlt ? '' : 'none';
  };
  [hasExisting, hasCredentials, willingNew].forEach((el) => {
    if (el) el.addEventListener('change', applyDomainVisibility);
  });
  applyDomainVisibility();

  const hasVariants = form.querySelector('[data-has-variants]');
  const variantBlocks = Array.from(form.querySelectorAll('[data-variant-fields]'));
  const variantMode = form.querySelector('[data-variant-mode]');
  const variantSeparateCount = form.querySelector('[data-variant-separate-count]');
  const panelHasCreds = form.querySelector('[data-ecom-panel-has-credentials]');
  const panelCredBlocks = Array.from(form.querySelectorAll('[data-ecom-panel-credential-fields]'));

  const setBlockEnabled = (block, enabled) => {
    if (!block) return;
    block.style.display = enabled ? '' : 'none';
    const inputs = Array.from(block.querySelectorAll('input, textarea, select'));
    inputs.forEach((el) => {
      el.disabled = !enabled;
    });
  };

  const applyVariantsVisibility = () => {
    const enabled = !!(hasVariants && hasVariants.checked);
    variantBlocks.forEach((b) => setBlockEnabled(b, enabled));
    if (!enabled) {
      if (variantMode) variantMode.value = '';
    }
    const separate = enabled && (variantMode && variantMode.value === 'separate_products');
    if (variantSeparateCount) {
      setBlockEnabled(variantSeparateCount, separate);
    }
  };

  const applyPanelVisibility = () => {
    const enabled = !!(panelHasCreds && panelHasCreds.checked);
    panelCredBlocks.forEach((b) => setBlockEnabled(b, enabled));
  };

  if (hasVariants) hasVariants.addEventListener('change', applyVariantsVisibility);
  if (variantMode) variantMode.addEventListener('change', applyVariantsVisibility);
  if (panelHasCreds) panelHasCreds.addEventListener('change', applyPanelVisibility);

  applyVariantsVisibility();
  applyPanelVisibility();

  const suggestBtn = form.querySelector('[data-domain-suggest]');
  const suggestQ = form.querySelector('[data-domain-suggest-q]');
  const suggestResults = form.querySelector('[data-domain-suggest-results]');
  const domainToRegister = form.querySelector('[data-domain-to-buy]');
  const renderSuggestions = (items) => {
    if (!suggestResults) return;
    suggestResults.innerHTML = '';
    (items || []).forEach((d) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'pill';
      btn.textContent = d;
      btn.addEventListener('click', () => {
        if (domainToRegister) {
          domainToRegister.value = d;
          domainToRegister.dispatchEvent(new Event('input', { bubbles: true }));
        }
      });
      suggestResults.appendChild(btn);
    });
  };
  if (suggestBtn && suggestQ && suggestResults) {
    suggestBtn.addEventListener('click', async () => {
      const q = (suggestQ.value || '').trim();
      if (!q) {
        renderSuggestions([]);
        return;
      }
      suggestBtn.disabled = true;
      try {
        const res = await fetch(`/domain/suggest?q=${encodeURIComponent(q)}`, { headers: { 'Accept': 'application/json' } });
        const data = await res.json();
        renderSuggestions(Array.isArray(data) ? data : []);
      } catch {
        renderSuggestions([]);
      } finally {
        suggestBtn.disabled = false;
      }
    });
  }

  const availabilityBtn = form.querySelector('[data-domain-availability]');
  const availabilityResult = form.querySelector('[data-domain-availability-result]');
  const availabilityCheckedAt = form.querySelector('[data-domain-availability-checked-at]');
  const availabilityStatus = form.querySelector('[data-domain-availability-status]');
  const availabilityDetails = form.querySelector('[data-domain-availability-details]');
  const toLocalIso = () => {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  };
  const pickDomainToBuy = () => (domainToRegister ? String(domainToRegister.value || '').trim() : '');
  if (availabilityBtn && availabilityResult) {
    availabilityBtn.addEventListener('click', async () => {
      const domain = pickDomainToBuy();
      if (!domain) {
        availabilityResult.textContent = 'Inserisci un dominio da verificare.';
        return;
      }
      availabilityBtn.disabled = true;
      availabilityResult.textContent = 'Verifica in corso...';
      try {
        const res = await fetch(`/domain/availability?domain=${encodeURIComponent(domain)}`, { headers: { 'Accept': 'application/json' } });
        const data = await res.json();
        const msg = data.message || 'Verifica completata';
        availabilityResult.textContent = msg;
        if (availabilityCheckedAt) availabilityCheckedAt.value = toLocalIso();
        if (availabilityStatus) availabilityStatus.value = String(data.status || '');
        if (availabilityDetails) availabilityDetails.value = String(data.details || '');
      } catch {
        availabilityResult.textContent = 'Errore durante la verifica.';
        if (availabilityCheckedAt) availabilityCheckedAt.value = toLocalIso();
        if (availabilityStatus) availabilityStatus.value = 'error';
      } finally {
        availabilityBtn.disabled = false;
      }
    });
  }

  const contractSent = form.querySelector('input[name="contractSentAt"]');
  const contractSigned = form.querySelector('input[name="contractSignedAt"]');
  if (contractSent && contractSigned) {
    const syncContract = () => syncMinDate(contractSent, contractSigned);
    contractSent.addEventListener('input', syncContract);
    contractSigned.addEventListener('input', syncContract);
    syncContract();
  }
})();
