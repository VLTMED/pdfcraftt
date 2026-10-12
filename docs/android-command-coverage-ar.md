# جرد أوامر PdfCraft مقابل Android

> **قيد صارم:** هذا الجرد لا يساوي بين عرض أداة JSON وبين نقل واجهتها. وجود رابط Automation يعني وجود مدخل أداة في المصدر، لا يثبت أن سير العمل المرئي على Android اكتمل. لا يُعلن تكافؤ 100% قبل تنفيذ كل صف والتحقق منه.

## أرقام مصدر الحقيقة

- الجرد مأخوذ آلياً من `crates/engine/src/commands.rs` و`crates/automation/src/tools.rs` في فرع Android الحالي.
- عدد أوامر سجل الواجهة: **190**؛ عدد الأدوات في Automation: **153**.
- أوامر لها `tool_for_command` صريح: **62**؛ أوامر بلا ربط مباشر: **128**.
- الأداة العامة `command_run` ترفض الأمر الذي لا يملك ربطاً صريحاً، برسالة `has no headless tool`؛ لذلك فالأوامر الـ128 تحتاج مسار Compose/Android أصلياً أو إضافة أداة Rust مع اختبار، ولا تُحسب منقولة لمجرد ظهورها في registry.
- تطبيق Android في هذا الفرع يتيح كتالوج الأدوات الـ153 عبر نماذج JSON Schema، لكن ذلك **واجهة وصول عامة فقط**؛ لا يعوّض تفاعلات التحرير بالرسم، والتنقل، والتأشير، والملفات الحديثة، والطباعة وغيرها.

## جميع أوامر المصدر

| # | المعرّف | القسم | الاسم الأصلي | ربط Automation | وضع Android في هذه الشريحة |
|---:|---|---|---|---|---|
| 1 | `file.open` | File | Open… | `doc_open` | فتح SAF موجود؛ يحتاج اختبارات دورة الحياة ومقارنة النتائج. |
| 2 | `file.open_recent` | File | Open Recent | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 3 | `file.clear_recent` | — | Clear Recent Files | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 4 | `file.pin_folder` | File | Pin folder to Home… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 5 | `create.blank` | File | New blank PDF | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 6 | `measure.distance` | — | Measure distance | `measure_distance` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 7 | `measure.perimeter` | — | Measure perimeter | `measure_perimeter` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 8 | `measure.area` | — | Measure area | `measure_area` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 9 | `measure.scale` | — | Set measurement scale | `measure_scale` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 10 | `measure.info` | — | Measurement information | `measure_info` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 11 | `measure.snap` | — | Measurement snapping | `measure_snap` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 12 | `measure.export` | — | Export measurements as CSV | `measure_export` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 13 | `page.copy` | — | Copy pages | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 14 | `page.cut` | — | Cut pages | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 15 | `page.paste` | — | Paste pages | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 16 | `create.file` | File | Create PDF from file… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 17 | `create.multiple` | File | Create PDF from multiple files… | `doc_create_multiple` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 18 | `create.multiple_separate` | File | Create separate PDFs… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 19 | `create.images` | File | Create PDF from images… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 20 | `create.clipboard` | File | Create PDF from clipboard | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 21 | `page.combine` | File | Combine files… | `doc_combine` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 22 | `file.save` | File | Save | `doc_save` | حفظ incremental/full موجود عبر مسار JNI/SAF أولي؛ الذرّية وround-trip غير مثبتين. |
| 23 | `file.save_as` | File | Save as… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 24 | `file.close` | File | Close file | `doc_close` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 25 | `file.close_all` | File | Close all | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 26 | `file.revert` | File | Revert | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 27 | `print.dialog` | File | Print… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 28 | `file.properties` | File | Document properties… | `doc_info` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 29 | `edit.undo` | Edit | Undo | `edit_undo` | أزرار أولية في Compose؛ لم تُختبر سلسلة التاريخ بعمق. |
| 30 | `edit.redo` | Edit | Redo | `edit_redo` | أزرار أولية في Compose؛ لم تُختبر سلسلة التاريخ بعمق. |
| 31 | `edit.find` | Edit | Find… | `text_find` | بحث أساسي مع نتائج صفحات؛ advanced search غير مكتمل. |
| 32 | `edit.advanced_search` | Edit | Advanced search… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 33 | `view.palette` | View | Find tools and commands… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 34 | `view.layout.single` | — | Single page | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 35 | `view.layout.two_up` | — | Two-page view | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 36 | `view.continuous` | — | Continuous scrolling | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 37 | `view.layout.cover` | — | Show cover page in two-page view | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 38 | `view.fit_width_scrolling` | — | Fit to width scrolling | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 39 | `view.fit_one_page` | — | Fit one full page | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 40 | `view.fit_visible` | — | Fit visible | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 41 | `view.marquee_zoom` | View | Marquee zoom | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 42 | `edit.column_select` | Edit | Column select | `text_extract` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 43 | `edit.snapshot` | Edit | Take a snapshot | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 44 | `view.full_screen` | View | Full screen mode | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 45 | `view.read_mode` | View | Read mode | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 46 | `view.focus_page_input` | View | Go to page… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 47 | `ui.dock.reset` | View | Reset panel layout | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 48 | `view.theme` | — | Switch light / dark theme | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 49 | `view.theme.system` | — | Use system setting | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 50 | `view.theme.light` | — | Light gray | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 51 | `view.theme.dark` | — | Dark gray | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 52 | `comment.list` | View | Comments panel | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 53 | `comment.note` | — | Add a sticky note | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 54 | `comment.freetext` | — | Add a text box | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 55 | `comment.highlight` | — | Highlight text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 56 | `comment.underline` | — | Underline text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 57 | `comment.strikeout` | — | Strikethrough text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 58 | `comment.squiggly` | — | Squiggly underline text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 59 | `comment.ink` | — | Draw freehand | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 60 | `comment.line` | — | Draw a line | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 61 | `comment.arrow` | — | Draw an arrow | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 62 | `comment.square` | — | Draw a rectangle | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 63 | `comment.circle` | — | Draw an oval | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 64 | `comment.polygon` | — | Draw a polygon | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 65 | `comment.polyline` | — | Draw connected lines | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 66 | `comment.cloud` | — | Draw a cloud | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 67 | `comment.callout` | — | Add a callout | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 68 | `comment.caret` | — | Insert text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 69 | `comment.replace` | — | Replace text | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 70 | `comment.attach` | — | Attach a file | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 71 | `comment.eraser` | — | Erase drawings | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 72 | `form.fields` | View | Form fields panel | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 73 | `form.clear` | Edit | Clear form | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 74 | `comment.flatten` | — | Flatten comments | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 75 | `comment.import` | — | Import comments… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 76 | `comment.stamp` | — | Add a stamp | `stamp_custom` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 77 | `comment.export` | — | Export all comments to data file… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 78 | `comment.hide_all` | — | Hide all comments | `comments_hide` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 79 | `comment.summarize` | — | Summarize comments | `comments_summarize` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 80 | `form.import_data` | — | Import form data… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 81 | `form.export_data` | — | Export form data… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 82 | `standards.pdfa` | — | PDF/A… | `pdfa_convert` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 83 | `actions.wizard` | — | Action Wizard… | `action_run` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 84 | `actions.distribution` | — | Prepare for distribution… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 85 | `actions.optimize_scans` | — | Optimize scanned documents… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 86 | `doc.compare` | — | Compare files… | `doc_compare_mark` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 87 | `form.detect` | — | Detect form fields | `form_detect_fields` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 88 | `form.merge_data` | — | Merge data files into spreadsheet… | `form_merge_data` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 89 | `form.flatten` | — | Flatten form fields | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 90 | `form.prepare` | — | Prepare a form | `form_set_actions` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 91 | `form.tab_order.row` | — | Tab order: by rows | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 92 | `form.tab_order.column` | — | Tab order: by columns | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 93 | `form.tab_order.structure` | — | Tab order: by document structure | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 94 | `edit.text` | — | Add text | `page_add_text` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 95 | `edit.edit_text` | — | Edit text & images | `text_edit` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 96 | `edit.link` | — | Add or edit links | `link_add` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 97 | `edit.links_from_urls` | — | Create links from URLs | `links_from_urls` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 98 | `edit.remove_links` | — | Remove all links | `links_remove` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 99 | `edit.bates` | — | Add Bates numbering… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 100 | `edit.image` | — | Add image… | `page_add_image` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 101 | `redact.mark` | — | Redact text and images | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 102 | `redact.pages` | — | Redact pages… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 103 | `redact.search` | — | Find text and redact… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 104 | `redact.properties` | — | Redaction properties… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 105 | `redact.apply` | — | Apply redactions… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 106 | `redact.clear` | — | Clear redaction marks | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 107 | `protect.remove_hidden` | — | Remove hidden information… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 108 | `redact.sanitize` | — | Sanitize document… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 109 | `form.field.properties` | — | Field properties… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 110 | `form.add.text` | — | Add a text field | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 111 | `form.add.checkbox` | — | Add a checkbox | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 112 | `form.add.radio` | — | Add a radio button | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 113 | `form.add.combo` | — | Add a drop-down list | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 114 | `form.add.list` | — | Add a list box | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 115 | `form.add.button` | — | Add a button | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 116 | `form.add.image` | — | Add an image field | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 117 | `form.add.date` | — | Add a date field | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 118 | `form.add.signature` | — | Add a digital signature field | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 119 | `sign.digital` | — | Digitally sign | `sign_document` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 120 | `sign.certify` | — | Certify (visible signature) | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 121 | `sign.certify_invisible` | — | Certify (invisible signature) | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 122 | `sign.validate` | — | Validate all signatures | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 123 | `sign.panel` | View | Signatures panel | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 124 | `sign.fill.text` | — | Fill & Sign: add text | `fill_sign_add` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 125 | `sign.fill.check` | — | Fill & Sign: checkmark | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 126 | `sign.fill.cross` | — | Fill & Sign: cross | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 127 | `sign.fill.dot` | — | Fill & Sign: dot | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 128 | `sign.fill.line` | — | Fill & Sign: line | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 129 | `sign.fill.date` | — | Fill & Sign: date | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 130 | `sign.fill.signature` | — | Fill & Sign: sign | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 131 | `sign.fill.initials` | — | Fill & Sign: initials | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 132 | `sign.fill.signature.change` | — | Fill & Sign: change signature | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 133 | `sign.fill.signature.remove` | — | Fill & Sign: remove saved signature | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 134 | `sign.fill.initials.remove` | — | Fill & Sign: remove saved initials | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 135 | `sign.fill.initials.change` | — | Fill & Sign: change initials | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 136 | `export.image` | File | Export to image… | `doc_export_images` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 137 | `optimize.reduce` | File | Reduce file size… | `doc_reduce` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 138 | `optimize.advanced` | File | Optimize PDF… | `doc_optimize` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 139 | `export.text` | File | Export to text… | `doc_export_text` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 140 | `export.docx` | File | Export to Word… | `doc_export_office` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 141 | `export.pptx` | File | Export to PowerPoint… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 142 | `export.html` | File | Export to HTML… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 143 | `export.rtf` | File | Export to RTF… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 144 | `app.preferences` | Edit | Preferences… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 145 | `tools.js_console` | — | JavaScript console… | `js_run` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 146 | `tools.document_js` | — | Document JavaScripts… | `js_set_document_script` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 147 | `ocr.recognize` | — | Recognize text… | `ocr_status` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 148 | `ocr.enhance_camera` | — | Enhance camera image | `page_enhance_camera` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 149 | `ocr.recognize_batch` | — | Recognize text in multiple files… | `ocr_recognize_files` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 150 | `a11y.check` | — | Check for accessibility… | `accessibility_check` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 151 | `a11y.report` | — | Open accessibility report | `accessibility_report` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 152 | `a11y.reading_options` | — | Change reading options… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 153 | `a11y.alt_text` | — | Add alternate text… | `accessibility_set_alt` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 154 | `export.all_images` | File | Export all images… | `doc_export_all_images` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 155 | `edit.header_footer` | — | Add header & footer… | `doc_header_footer` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 156 | `edit.header_footer.update` | — | Update header & footer… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 157 | `edit.header_footer.remove` | — | Remove header & footer | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 158 | `edit.watermark` | — | Add watermark… | `doc_watermark` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 159 | `edit.watermark.update` | — | Update watermark… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 160 | `edit.watermark.remove` | — | Remove watermark | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 161 | `edit.background` | — | Add background… | `doc_background` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 162 | `edit.background.update` | — | Update background… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 163 | `edit.background.remove` | — | Remove background | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 164 | `protect.password` | File | Protect using password… | `doc_protect` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 165 | `protect.remove` | File | Remove security | `doc_unprotect` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 166 | `protect.unlock` | — | Enter permissions password… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 167 | `protect.properties` | File | Security properties… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 168 | `page.organize` | Pages | Organize pages | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 169 | `bookmark.add` | Pages | New bookmark | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 170 | `bookmark.from_structure` | Pages | New bookmarks from structure | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 171 | `page.rotate` | Pages | Rotate pages clockwise | `page_rotate` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 172 | `page.rotate_ccw` | Pages | Rotate pages counterclockwise | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 173 | `page.delete` | Pages | Delete pages | `page_delete` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 174 | `page.insert_blank` | Pages | Insert blank page | `page_insert_blank` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 175 | `page.rotate_dialog` | Pages | Rotate pages… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 176 | `page.duplicate` | Pages | Duplicate pages | `page_duplicate` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 177 | `page.crop` | Pages | Crop pages | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 178 | `page.boxes` | Pages | Set page boxes… | `page_set_box` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 179 | `page.insert` | Pages | Insert pages from file… | `page_insert_file` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 180 | `page.replace` | Pages | Replace pages… | `page_replace` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 181 | `page.extract` | Pages | Extract pages… | `page_extract` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 182 | `page.split` | Pages | Split document… | `doc_split` | الوظيفة متاحة كأداة عامة؛ سير الأمر المرئي والتكافؤ لم يُثبتا. |
| 183 | `page.number` | Pages | Number pages… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 184 | `help.shortcuts` | Help | Keyboard shortcuts | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 185 | `help.discord` | Help | Join the ArtCraft Discord | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 186 | `help.app_page` | Help | PdfCraft web page | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 187 | `help.github` | Help | PdfCraft on GitHub | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 188 | `help.website` | Help | ArtCraft website | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 189 | `help.check_updates` | Help | Check for updates… | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |
| 190 | `help.about` | Help | About PdfCraft | — | لا ربط مباشر؛ يتطلب محول Android أصلياً أو أداة Rust جديدة واختباراً. |

## الأوامر غير المرتبطة حالياً

عددها **128**. هذه قائمة عمل وليست استثناءات من المتطلبات:

- `file.open_recent` — Open Recent (File)
- `file.clear_recent` — Clear Recent Files (—)
- `file.pin_folder` — Pin folder to Home… (File)
- `create.blank` — New blank PDF (File)
- `page.copy` — Copy pages (—)
- `page.cut` — Cut pages (—)
- `page.paste` — Paste pages (—)
- `create.file` — Create PDF from file… (File)
- `create.multiple_separate` — Create separate PDFs… (File)
- `create.images` — Create PDF from images… (File)
- `create.clipboard` — Create PDF from clipboard (File)
- `file.save_as` — Save as… (File)
- `file.close_all` — Close all (File)
- `file.revert` — Revert (File)
- `print.dialog` — Print… (File)
- `edit.advanced_search` — Advanced search… (Edit)
- `view.palette` — Find tools and commands… (View)
- `view.layout.single` — Single page (—)
- `view.layout.two_up` — Two-page view (—)
- `view.continuous` — Continuous scrolling (—)
- `view.layout.cover` — Show cover page in two-page view (—)
- `view.fit_width_scrolling` — Fit to width scrolling (—)
- `view.fit_one_page` — Fit one full page (—)
- `view.fit_visible` — Fit visible (—)
- `view.marquee_zoom` — Marquee zoom (View)
- `edit.snapshot` — Take a snapshot (Edit)
- `view.full_screen` — Full screen mode (View)
- `view.read_mode` — Read mode (View)
- `view.focus_page_input` — Go to page… (View)
- `ui.dock.reset` — Reset panel layout (View)
- `view.theme` — Switch light / dark theme (—)
- `view.theme.system` — Use system setting (—)
- `view.theme.light` — Light gray (—)
- `view.theme.dark` — Dark gray (—)
- `comment.list` — Comments panel (View)
- `comment.note` — Add a sticky note (—)
- `comment.freetext` — Add a text box (—)
- `comment.highlight` — Highlight text (—)
- `comment.underline` — Underline text (—)
- `comment.strikeout` — Strikethrough text (—)
- `comment.squiggly` — Squiggly underline text (—)
- `comment.ink` — Draw freehand (—)
- `comment.line` — Draw a line (—)
- `comment.arrow` — Draw an arrow (—)
- `comment.square` — Draw a rectangle (—)
- `comment.circle` — Draw an oval (—)
- `comment.polygon` — Draw a polygon (—)
- `comment.polyline` — Draw connected lines (—)
- `comment.cloud` — Draw a cloud (—)
- `comment.callout` — Add a callout (—)
- `comment.caret` — Insert text (—)
- `comment.replace` — Replace text (—)
- `comment.attach` — Attach a file (—)
- `comment.eraser` — Erase drawings (—)
- `form.fields` — Form fields panel (View)
- `form.clear` — Clear form (Edit)
- `comment.flatten` — Flatten comments (—)
- `comment.import` — Import comments… (—)
- `comment.export` — Export all comments to data file… (—)
- `form.import_data` — Import form data… (—)
- `form.export_data` — Export form data… (—)
- `actions.distribution` — Prepare for distribution… (—)
- `actions.optimize_scans` — Optimize scanned documents… (—)
- `form.flatten` — Flatten form fields (—)
- `form.tab_order.row` — Tab order: by rows (—)
- `form.tab_order.column` — Tab order: by columns (—)
- `form.tab_order.structure` — Tab order: by document structure (—)
- `edit.bates` — Add Bates numbering… (—)
- `redact.mark` — Redact text and images (—)
- `redact.pages` — Redact pages… (—)
- `redact.search` — Find text and redact… (—)
- `redact.properties` — Redaction properties… (—)
- `redact.apply` — Apply redactions… (—)
- `redact.clear` — Clear redaction marks (—)
- `protect.remove_hidden` — Remove hidden information… (—)
- `redact.sanitize` — Sanitize document… (—)
- `form.field.properties` — Field properties… (—)
- `form.add.text` — Add a text field (—)
- `form.add.checkbox` — Add a checkbox (—)
- `form.add.radio` — Add a radio button (—)
- `form.add.combo` — Add a drop-down list (—)
- `form.add.list` — Add a list box (—)
- `form.add.button` — Add a button (—)
- `form.add.image` — Add an image field (—)
- `form.add.date` — Add a date field (—)
- `form.add.signature` — Add a digital signature field (—)
- `sign.certify` — Certify (visible signature) (—)
- `sign.certify_invisible` — Certify (invisible signature) (—)
- `sign.validate` — Validate all signatures (—)
- `sign.panel` — Signatures panel (View)
- `sign.fill.check` — Fill & Sign: checkmark (—)
- `sign.fill.cross` — Fill & Sign: cross (—)
- `sign.fill.dot` — Fill & Sign: dot (—)
- `sign.fill.line` — Fill & Sign: line (—)
- `sign.fill.date` — Fill & Sign: date (—)
- `sign.fill.signature` — Fill & Sign: sign (—)
- `sign.fill.initials` — Fill & Sign: initials (—)
- `sign.fill.signature.change` — Fill & Sign: change signature (—)
- `sign.fill.signature.remove` — Fill & Sign: remove saved signature (—)
- `sign.fill.initials.remove` — Fill & Sign: remove saved initials (—)
- `sign.fill.initials.change` — Fill & Sign: change initials (—)
- `export.pptx` — Export to PowerPoint… (File)
- `export.html` — Export to HTML… (File)
- `export.rtf` — Export to RTF… (File)
- `app.preferences` — Preferences… (Edit)
- `a11y.reading_options` — Change reading options… (—)
- `edit.header_footer.update` — Update header & footer… (—)
- `edit.header_footer.remove` — Remove header & footer (—)
- `edit.watermark.update` — Update watermark… (—)
- `edit.watermark.remove` — Remove watermark (—)
- `edit.background.update` — Update background… (—)
- `edit.background.remove` — Remove background (—)
- `protect.unlock` — Enter permissions password… (—)
- `protect.properties` — Security properties… (File)
- `page.organize` — Organize pages (Pages)
- `bookmark.add` — New bookmark (Pages)
- `bookmark.from_structure` — New bookmarks from structure (Pages)
- `page.rotate_ccw` — Rotate pages counterclockwise (Pages)
- `page.rotate_dialog` — Rotate pages… (Pages)
- `page.crop` — Crop pages (Pages)
- `page.number` — Number pages… (Pages)
- `help.shortcuts` — Keyboard shortcuts (Help)
- `help.discord` — Join the ArtCraft Discord (Help)
- `help.app_page` — PdfCraft web page (Help)
- `help.github` — PdfCraft on GitHub (Help)
- `help.website` — ArtCraft website (Help)
- `help.check_updates` — Check for updates… (Help)
- `help.about` — About PdfCraft (Help)

## معيار إغلاق الفجوة

لكل معرّف: (1) تحديد سلوكه الفعلي في واجهة PdfCraft المصدرية، (2) تنفيذ مسار لمس أصلي أو تعيينه إلى أداة Rust مطابقة دون فقد معامل، (3) فرض `enabled/Needs` والصلاحيات والتحذيرات، (4) اختبار النتيجة وundo/redo/save/reopen عند انطباقها، (5) إبقاء حالات المصدر الجزئية أو غير المدعومة ظاهرة. لا يُحسب مجرد صف قابل للنقر أو JSON form نجاحاً.
