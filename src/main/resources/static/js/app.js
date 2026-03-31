(function () {
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
  const projectOpen = existingModal?.querySelector('[data-project-open]');
  const projectStatus = existingModal?.querySelector('[data-project-status]');
  const modalCloseButtons = existingModal ? Array.from(existingModal.querySelectorAll('[data-modal-close]')) : [];
  let cachedProjects = [];

  const formatProjectDate = (value) => {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleDateString('it-IT', { day: '2-digit', month: 'short', year: 'numeric' });
  };

  const projectKindLabels = { vetrina: 'Vetrina', ecommerce: 'E-commerce' };
  const formatProjectKind = (value) => projectKindLabels[value] || value || '-';

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
      const time = document.createElement('span');
      time.textContent = formatProjectDate(item.updatedAt);
      meta.append(company, kind, time);
      button.append(title, meta);
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

  const applyProjectFilter = () => {
    const query = (projectSearch?.value || '').trim().toLowerCase();
    if (!query) {
      renderProjectList(cachedProjects);
      return;
    }
    const filtered = cachedProjects.filter(item => {
      const name = (item.projectName || '').toLowerCase();
      const company = (item.companyName || '').toLowerCase();
      const kind = (item.projectKind || '').toLowerCase();
      return name.includes(query) || company.includes(query) || kind.includes(query);
    });
    renderProjectList(filtered);
  };

  const fetchProjects = async () => {
    if (!projectList) return;
    projectList.innerHTML = '<div class="modal-empty">Caricamento...</div>';
    if (projectStatus) projectStatus.textContent = '';
    try {
      const response = await fetch('/intake/projects?limit=100', {
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
    if (!value) {
      if (projectStatus) projectStatus.textContent = 'Inserisci un nome progetto.';
      return;
    }
    const match = cachedProjects.find(item =>
      (item.projectName || '').toLowerCase() === value.toLowerCase()
    );
    if (match && match.projectId) {
      window.location.href = `/intake?projectId=${encodeURIComponent(match.projectId)}`;
      return;
    }
    window.location.href = `/intake?projectName=${encodeURIComponent(value)}`;
  };

  if (openExistingBtn && existingModal) {
    openExistingBtn.addEventListener('click', openProjectModal);
    modalCloseButtons.forEach(btn => btn.addEventListener('click', closeProjectModal));
    if (projectSearch) {
      projectSearch.addEventListener('input', applyProjectFilter);
      projectSearch.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
          event.preventDefault();
          openProjectByName();
        }
      });
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
    if (el.type === 'hidden') return false;
    if (el.type === 'checkbox' || el.type === 'radio') return el.checked;
    if (el.type === 'file') return el.files && el.files.length > 0;
    return el.value && el.value.trim().length > 0;
  };

  const getTrackedFields = () => {
    if (!form) return [];
    return Array.from(form.querySelectorAll('input, textarea, select'))
      .filter(field => {
        if (field.type === 'hidden') return false;
        if (field.hasAttribute('data-skip-progress')) return false;
        if (['submit', 'reset', 'button', 'image'].includes(field.type)) return false;
        return true;
      });
  };

  const isCompleted = (field) => {
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

  const updateProgress = () => {
    if (!progressTargets.length) return;
    const fields = getTrackedFields();
    const total = fields.length;
    const completed = fields.reduce((acc, field) => acc + (isCompleted(field) ? 1 : 0), 0);
    const percent = total ? Math.round((completed / total) * 100) : 0;
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
    let idx = 0;
    while (idx < sections.length && confirmed.has(sections[idx].id)) {
      idx += 1;
    }
    return Math.min(idx, sections.length - 1);
  };

  const updateNavState = () => {
    maxUnlockedIndex = computeMaxUnlocked();
    sections.forEach((section, index) => {
      const link = byId.get(section.id);
      if (!link) return;
      const locked = enforceSectionLock && index > maxUnlockedIndex;
      link.classList.toggle('locked', locked);
      link.classList.toggle('done', confirmed.has(section.id));
      link.setAttribute('aria-disabled', locked ? 'true' : 'false');
      link.tabIndex = locked ? -1 : 0;
      const badge = ensureBadge(link);
      if (confirmed.has(section.id)) {
        badge.textContent = 'Completato';
      } else if (locked) {
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

  const setFieldLock = (section, locked) => {
    const fields = Array.from(section.querySelectorAll('input, textarea, select'));
    fields.forEach(field => {
      if (field.type === 'hidden') return;
      if (locked) {
        field.setAttribute('data-locked', 'true');
        field.setAttribute('aria-disabled', 'true');
        field.setAttribute('tabindex', '-1');
        if (typeof field.readOnly !== 'undefined' && field.tagName !== 'SELECT') {
          field.readOnly = true;
        }
      } else {
        field.removeAttribute('data-locked');
        field.removeAttribute('aria-disabled');
        field.removeAttribute('tabindex');
        if (typeof field.readOnly !== 'undefined' && field.tagName !== 'SELECT') {
          field.readOnly = false;
        }
      }
      const wrapper = field.closest('.field') || field.closest('.check-row') || field.closest('.pill');
      if (wrapper) {
        wrapper.classList.toggle('field-locked', locked);
      }
    });
  };

  const validateSection = (section) => {
    let valid = true;
    const required = Array.from(section.querySelectorAll('[required]'));
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
    maxUnlockedIndex = computeMaxUnlocked();
    sections.forEach((section, index) => {
      const locked = enforceSectionLock && index > maxUnlockedIndex;
      section.classList.toggle('section-locked', locked);
      const button = section.querySelector('.section-confirm');
      const edit = section.querySelector('.section-edit');
      if (button) {
        const isConfirmed = confirmed.has(section.id);
        button.disabled = locked || isConfirmed;
        button.textContent = isConfirmed ? 'Sezione confermata' : 'Conferma sezione';
      }
      if (edit) {
        edit.hidden = !confirmed.has(section.id);
        edit.disabled = locked;
      }
      setFieldLock(section, locked || confirmed.has(section.id));
    });
    updateNavState();
  };

  const confirmSection = async (section) => {
    const index = sections.indexOf(section);
    if (enforceSectionLock && index > maxUnlockedIndex) {
      return;
    }
    if (!validateSection(section)) {
      return;
    }
    const draftIdInput = document.getElementById('draftId');
    const rawDraftId = draftIdInput?.value;
    const rawProjectId = document.getElementById('projectId')?.value;
    const draftId = rawDraftId && rawDraftId.trim().length ? rawDraftId : null;
    const projectId = rawProjectId && rawProjectId.trim().length ? rawProjectId : null;
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
    const csrfHeader = document.getElementById('csrfHeader')?.value || 'X-CSRF-TOKEN';

    try {
      const previousMax = maxUnlockedIndex;
      const response = await fetch('/intake/section/confirm', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ draftId, projectId, sectionKey: section.id })
      });
      if (!response.ok) {
        throw new Error('confirm failed');
      }
      const data = await response.json();
      if (data.draftId && draftIdInput) {
        draftIdInput.value = data.draftId;
      }
      confirmed.clear();
      (data.confirmedSections || []).forEach(s => confirmed.add(s));
      lockSections();
      if (maxUnlockedIndex > previousMax && maxUnlockedIndex < sections.length) {
        sections[maxUnlockedIndex].scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    } catch (err) {
      const alert = section.querySelector('.section-alert');
      if (alert) {
        alert.textContent = 'Errore nel salvataggio della conferma. Riprova.';
        alert.hidden = false;
      }
    }
  };

  const editSection = async (section) => {
    const draftIdInput = document.getElementById('draftId');
    const rawDraftId = draftIdInput?.value;
    const rawProjectId = document.getElementById('projectId')?.value;
    const draftId = rawDraftId && rawDraftId.trim().length ? rawDraftId : null;
    const projectId = rawProjectId && rawProjectId.trim().length ? rawProjectId : null;
    const csrfToken = document.querySelector('input[name="_csrf"]')?.value;
    const csrfHeader = document.getElementById('csrfHeader')?.value || 'X-CSRF-TOKEN';
    try {
      const response = await fetch('/intake/section/edit', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken
        },
        body: JSON.stringify({ draftId, projectId, sectionKey: section.id })
      });
      if (!response.ok) {
        throw new Error('edit failed');
      }
      const data = await response.json();
      if (data.draftId && draftIdInput) {
        draftIdInput.value = data.draftId;
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
    // left summary scrolls with the page; no internal centering
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
      if (enforceSectionLock && link.classList.contains('locked')) {
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
    scrollLock = true;
    sections[maxUnlockedIndex].scrollIntoView({ behavior: 'smooth', block: 'start' });
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

  const contractSent = form.querySelector('input[name="contractSentAt"]');
  const contractSigned = form.querySelector('input[name="contractSignedAt"]');
  if (contractSent && contractSigned) {
    const syncContract = () => syncMinDate(contractSent, contractSigned);
    contractSent.addEventListener('input', syncContract);
    contractSigned.addEventListener('input', syncContract);
    syncContract();
  }
})();







