# مصفوفة تكافؤ Android — خط أساس المصدر وفجوات النقل

> **الغرض:** توثيق خط أساس PdfCraft المكتبي ثم delta فرع Android بتاريخ 2026-10-12. صفوف المصفوفة الأصلية تمثل تدقيقاً سابقاً للتنفيذ الجاري؛ لم تُرقَّ حالة أي ميزة لمجرد وجود API أو نموذج JSON. هذه الوثيقة ليست شهادة تكافؤ ولا تحسب نسبة كلية.

## 1. المنهجية والنطاق

- اعتمد خط الأساس على نتائج التدقيق ومسارات المصدر؛ التحديث الحالي أضاف جرداً آلياً لسجل الأوامر واختبارات Rust لجسر Android. لم تُشغّل حتى الآن اختبارات Android instrumentation أو اختبارات parity البصرية.
- اعتبرتُ وجود API أو اختبار Rust دليلاً على قدرة مصدرية، لا دليلاً تلقائياً على ميزة مستخدم مكتملة أو تكافؤ Acrobat.
- كان Android قبل هذا الفرع يقتصر على فتح/تحليل/raster. الفرع الحالي أضاف مساحة Automation مستمرة، 153 نموذج أداة، حفظاً/تصديراً مجزّأً، بحثاً/undo/redo، وعارضاً أساسياً؛ تبقى واجهات متخصصة واختبارات التكافؤ غير مكتملة.
- حالات **PdfCraft** في الجدول: `منفذ`، `جزئي`، `غير واضح`. حالات **Android**: `جزئي`، `مفقود`، `تجريبي محدود`.
- «فجوة النقل» تعني ما يلزم لبناء binding/session/UI/SAF lifecycle، لا أنها تثبت أن التنفيذ المقترح موجود.
- اختبارات Rust host الحالية: **3 ناجحة**. `cargo clippy -D warnings` تعثر بسبب lint موجود في `crates/engine/src/camera_edges.rs:235`، ولم يُبن APK Android الموسع على CI بعد.

### Delta تنفيذ Android بتاريخ 2026-10-12

- جسر JNI يدير Automation workspace مستمراً متعدد المستندات، مع SAF مرحلي للإدخال، فتح بكلمة مرور، rendering، وواجهات أدوات schema-driven.
- عدد أدوات Automation الفعلي **153**. سجل المصدر يحتوي **190 أمراً**؛ **62** منها فقط مرتبطة بأداة عبر `tool_for_command`، و**128** بلا ربط مباشر. أضيف تبويب يعرض الأمر وحالته؛ يعالج Android حالياً مجموعة صغيرة من أوامر الفتح/الحفظ/التراجع/البحث/الوضع الواحد، ويستعمل `command_run` للأوامر المرتبطة، بينما تبقى الأوامر الأخرى غير المنفذة معلّمة.
- هذا delta لا يثبت fidelity للعارض (صورة صفحة كاملة بلا tiling/zoom متقدم)، ولا instrumentation، lifecycle، واجهات تخصصية للتعليقات/النماذج/التوقيع/التنقيح، أو round-trip لكل أداة. تفاصيل الأوامر الـ190 في [الجرد الكامل](android-command-coverage-ar.md).

## 2. مصفوفة الميزات

| # | المنطقة | القدرة | حالة PdfCraft | دليل المصدر (مختصر) | حالة Android الأولية قبل delta | فجوة النقل | اختبار parity مقترح |
|---:|---|---|---|---|---|---|---|
| 1 | الفتح/COS | فتح bytes، COS lazy، catalog، streams وxref | منفذ | `crates/cos/src/document.rs`, `README.md`, `object.rs` | جزئي | `DocumentSession` opaque يعيد استخدام Document بدلاً من إعادة parse | corpus classic/xref-stream/hybrid/object-stream؛ قارن root/version/page count والأخطاء |
| 2 | الفتح/COS | إصلاح xref مع `repair_log` قابل للعرض | منفذ | `document.rs` repair tests، `tests/writer.rs` | مفقود | JNI يعيد نتيجة منظمة تشمل repaired/log، لا `OK` فقط | malformed xref؛ قارن log حرفياً وميّز repaired عن سالم |
| 3 | الصفحات | page tree، inheritance، Crop/Rotate/UserUnit/display size/view matrix | منفذ | `crates/model/src/lib.rs` واختباراته | جزئي | DTO `PageInfo` كامل، لا page count فقط | golden لـCrop خارج MediaBox، rotation، inheritance، UserUnit؛ قارن التحويل والرستر |
| 4 | الصيغة | قراءة version/headers/كل أشكال xref المدعومة | منفذ | `parser.rs`, `cos/README.md` | جزئي | تقرير قبول/رفض وحدود الصيغة؛ لا ادعاء linearization أو كل PDF | corpus extensions؛ قارن parser result وunsupported typed errors |
| 5 | الأمن | user/owner password، auth، permissions | منفذ | `Document::unlock/security/permissions`, `crypt` tests | جزئي | `SecurityInfo` وUX يميز Auth/permissions؛ حماية الأسرار | user/owner/empty/wrong لكل fixture؛ قارن auth والقيود |
| 6 | الأمن | قراءة/كتابة RC4/AES، crypt filters، metadata وEFF | منفذ | `security.rs`, `crypt`, `encryption.rs` | مفقود | `setEncryption/removeEncryption` وfull/incremental save عبر SAF | خوارزميات R2–R6، metadata، EFF، reopen ورفض malformed |
| 7 | الحفظ | COS COW، revisions، modified objects، full/incremental | منفذ | `document.rs`, `writer.rs`, `tests/writer.rs` | مفقود | session mutation، `SaveOptions`، prefix-preserving incremental | full مقابل incremental، revisions، independent parser وrepair_log فارغ |
| 8 | الحفظ | staging/ID/ModDate وatomic SAF output | منفذ | `platform/staging.rs`, writer | مفقود | CreateDocument/temporary staging/commit، عدم الكتابة فوق input | collision، crash/cancel/process death؛ original يبقى سليماً |
| 9 | properties | Info/trailer/XMP وembedded files/attachments typed | جزئي | COS raw APIs؛ لا typed Filespec/EmbeddedFiles tests | مفقود | إضافة typed APIs قبل claim parity؛ raw adapter مؤقت فقط | fixtures `/Info`, `/Metadata`, `Names/EmbeddedFiles`; expected raw/unsupported صراحة |
| 10 | الحفظ | reopen/round-trip بعد كل save وlarge/shared streams | منفذ | writer/bounded_writer/shared_streams tests | مفقود | `reopenSaved` وreport للrevision/repair | save ثم reopen، eviction، shared streams، memory limits |
| 11 | العرض | full-page CPU RGBA8، warnings/stats وحدود الحجم | منفذ | `render/raster.rs`, `RenderPool`, JNI render | جزئي | persistent session، cache، typed RenderWarning/Stats | pixel/dimension comparison مع premultiplication tolerance |
| 12 | العرض | tile/clip/preview/text render وcancellation/tags | منفذ | `RenderRequest::Tile`, `RequestKind::{Pixels,Text}` | مفقود | JNI tile/text packets، generation لمنع stale images | tile stitch مقابل full، zoom/pan generations، cancel |
| 13 | العرض | zoom/pan/fit/layout continuous/two-up/cover | منفذ | `ui-egui/canvas.rs`, viewer tests | مفقود | Compose viewport/PageXform وtouch/keyboard | fit modes، pinch/pan، anchor، layouts وrotation |
| 14 | النص | glyphs، Unicode، boxes، plain text، selection/copy | منفذ | `render/text.rs`, text-select tests | مفقود | Text DTO، selection/accessibility/copy؛ لا OCR بديل | CJK/RTL/ligature؛ قارن glyph boxes والنسخ |
| 15 | البحث | case/whole-word/advanced/context/next-prev | منفذ | `PageText::find_opts`, `search_ui`, viewer tests | مفقود | `NativePdf.search` وUI results | corpus Unicode؛ قارن ranges/context/limits |
| 16 | الروابط | URI وpage destinations XYZ/Fit* والتنقل | منفذ | `render::Link/LinkTarget/DestView`, engine links | مفقود | callback آمن للـURI وdestination navigation | internal/external/null operands؛ قارن page/scroll/zoom وsafe intents |
| 17 | الهيكل | outline/bookmarks، labels، search والتنقل | منفذ | `OutlineItem`, panels, bookmarks tests | مفقود | tree DTO وdepth/truncation معلن | named/invalid destinations وعمق كبير؛ قارن الشجرة والنقل |
| 18 | العرض | OCG/layers visibility وSetOCGState | منفذ | `Layer`, `RightPanel::Layers`, layer tests | مفقود | layer state إلى renderer وإبطال tiles دون dirty | Toggle/radio؛ قارن raster قبل/بعد وdirty |
| 19 | التنقل | thumbnails، scroll history، page modes/context menu | منفذ | viewer tests، thumbnails panel | جزئي | thumbnail packets، state persistence وcontinuous scroll | mixed page sizes، click thumbnail، process recreation |
| 20 | العرض | rotate view مقابل RotatePages التحريري | جزئي | `Edit::RotatePages`, PageXform؛ لا rotate-view مستقل مثبت | مفقود | فصل view transform عن edit وعدم تغيير PDF خطأً | 0/90/180/270 hit testing ثم saved RotatePages |
| 21 | تنظيم | rotate/delete/move pages ومنع حذف آخر صفحة | منفذ | `crates/organize`, engine dispatch/tests | مفقود | session Edit و0-based index وtyped errors | selected/first/last/missing؛ links/OpenAction ثم undo |
| 22 | تنظيم | blank/duplicate/copy-cut-paste/replace pages | منفذ | organize tests، `ui-egui/editing.rs` | مفقود | selection/clipboard/import وresource independence | order/resources/annotations، undo/redo وredo invalidation |
| 23 | تنظيم | PageBox/BoxSpec وMedia/Crop/Trim/Bleed/Art | منفذ | `organize/boxes.rs`, pageboxes tests | مفقود | DTO validation؛ عدم تسمية box crop rasterياً | inherited/default/reset؛ dictionaries والرستر |
| 24 | تنظيم | insert/import pages مع field conflicts وrewiring | منفذ | `organize/import.rs`, tests | مفقود | SAF source، policy Rename/Merge/Reject، diagnostics | source/subset، links/OCG/forms، reopen |
| 25 | تنظيم | extract/split/combine وbookmarks/metadata | منفذ | `import.rs`, split/combine tests | مفقود | outputs مستقلة لا undo تلقائياً؛ multi-output SAF | ranges/duplicates، metadata/bookmarks/attachments |
| 26 | التاريخ | undo/redo labels، dirty/generation، save/rebase/revert | منفذ | `engine::Session`, editing tests | مفقود | history داخل Rust handle لا Kotlin clone | سلسلة edits؛ undo حتى البداية/redo ثم edit جديد |
| 27 | التحرير | editable objects وmove objects (نص/صور) | منفذ | `edit/objects.rs`, `images.rs` | مفقود | list/hit-test/select/move مع MAX limits | shared Contents؛ حرّك صفحة وتأكد من عدم تغير الشريكة |
| 28 | التحرير | text lines/blocks، replace/clear/rewrite | منفذ | `edit/text.rs`, `text_regions.rs` | مفقود | Block/Range DTO ونتيجة substitution/decodable | Latin/Arabic/Thai/CJK؛ text+raster+font fallback |
| 29 | التحرير | add/update/delete text/images | منفذ | `edit/added.rs`, tests | مفقود | image SAF import، user-unit/rotation، save | list_added قبل/بعد، round-trip وout-of-range |
| 30 | التحرير | fonts/shaping وRTL واللغة | جزئي | `fonts/source_shaping.rs`, embedding؛ Arabic tests | مفقود | diagnostics لـsubstitution؛ لا ادعاء OpenType/Type3 كامل | glyph availability، fallback، visual/text comparison |
| 31 | التحرير | vector shapes المستقلة | غير واضح | `ObjectKind` لا يحتوي Path/Shape؛ Form artwork مجمع | مفقود | لا تعرض feature قبل تصميم API/tests | negative test: paths => unsupported/unclear لا تحويلها إلى Text/Image |
| 32 | التحرير | watermark/header/footer/background وflatten | منفذ/جزئي | `edit/lib.rs`, `flatten.rs`, refusal tests | مفقود | binding، fail-closed، full save | marks layer/order؛ flatten refusal atomicity |
| 33 | التعليقات | إنشاء/تعديل markup وshape وink وFreeText | منفذ | `annot/lib.rs`, `appearance.rs`, tests | مفقود | Annotation DTO/appearance/hit-test وCompose tools | كل subtype المثبت، rotation/opacity/appearance/reopen |
| 34 | التعليقات | replies/IRT/review states/Marked وحذف thread | منفذ | annot review tests وcomments panel | مفقود | thread graph/status API | nested replies، all states، deletion وunknown keys |
| 35 | التعليقات | panel filtering/search/sort وlocked | منفذ | `comments_panel.rs`, tests | مفقود | UI Compose؛ لا مساواة enum بواجهة مكتملة | filters Page/Author/Date/Type/Color/State |
| 36 | التعليقات | stamps/fill-sign/measurement وأدوات غير المكتملة | جزئي | annot README يذكر Not yet؛ measure بلا E2E كافٍ | مفقود | feature gating/refusal بدل نجاح صامت | implemented vs unsupported corpus؛ preserve appearance |
| 37 | redaction | marks/apply، إزالة glyph/images/paths/forms، fail-closed | منفذ | `redact/lib.rs/interp.rs`, tests | مفقود | confirmation، full rewrite، atomic destination | vertical/unreadable/residue/shared forms؛ bytes قبل/بعد |
| 38 | redaction | sanitize/remove hidden categories | جزئي | `sanitize.rs`, README gaps | مفقود | category policy وfull save؛ لا claim كل metadata/struct-tree | scan/remove كل category وunknown warnings |
| 39 | التدقيق | audit faux-redaction/unapplied marks | منفذ | `audit/lib.rs`, cover/tests | مفقود | `AuditReport` JNI وحدود blind spots | covers/Rotate/forms/translucent؛ findings/errors |
| 40 | النماذج | AcroForm tree/widgets/values/options/flags | منفذ | `forms/lib.rs`, forms tests | مفقود | typed field snapshot/session وrender invalidation | كل FieldKind والوراثة/widgets/reopen |
| 41 | النماذج | fill/reset/appearance/validation/field actions | منفذ | forms README/tests، forms UI | مفقود | IME/Tab/choices، typed errors وundo | Compose instrumentation مقابل forms tests |
| 42 | النماذج | add/delete/props/auto-detect وexport/import data | منفذ/جزئي | forms/actions، engine export؛ XDP محدود | مفقود | XFDF/FDF/XML/CSV/TXT DTO، XDP لا يمر كحزمة بلا contract | round-trip encodings, malformed XML limits |
| 43 | XFA/JS | dynamic/static XFA layout/events وFormCalc/JS | جزئي | `xfa/README`, `engine/xfa`, `js` tests؛ gaps موثقة | مفقود | sandbox/caps، alerts/requests، Android policy | initialize/calculate/change/click، row/layout limits |
| 44 | التوقيع | list/validate/sign PAdES/CMS وDocMDP | منفذ/جزئي | `sign` pdf/tests/UI؛ encrypted docs ترفض | مفقود | Keystore/credentialRef، TrustStore، incremental output | vectors، chain/status، encrypted refusal |
| 45 | الأمن | Digital ID PKCS#12، TSA، DSS/LTV | منفذ | `sign/pkcs12/timestamp/dss` | مفقود | Android Keystore/KeyChain وcaller-owned TSA | non-exportable key، timestamp trust، DSS/VRI |
| 46 | الحماية | PDF permissions مقابل SAF permissions | منفذ | `crypt::Permissions`, document permissions | مفقود | enforcement قبل كل action؛ لا خلط URI grant مع P bits | permission matrix user/owner وtool refusal |
| 47 | OCR | searchable image + invisible text layer | منفذ | `ocr/lib.rs`, `file-format-parity.md` | مفقود | model assets/filesDir، language gate، save | English/Latin models، NoModels، boxes/raster |
| 48 | الإنشاء | blank/from images/from text وimage extraction | منفذ | `create/lib.rs/tests` | مفقود | multi-file SAF وcreate/save APIs | DPI/ICC/alpha/TIFF، BOM/WinAnsi، reopen |
| 49 | التصدير | HTML/DOCX/RTF/PPTX/text وXFDF/FDF/data | منفذ/جزئي | `export`, `xfdf`, parity docs؛ Office lossy وXDP none | مفقود | typed format result، SAF output/share، warnings | ZIP/XML/openability، comments/forms/data round-trip |
| 50 | الطباعة | impose/print-ready PDF، CUPS/Windows spool | منفذ | `print/lib.rs/tests/spool.rs` | مفقود | Android `PrintDocumentAdapter` منفصل عن impose؛ لا CUPS | n-up/booklet/poster/page ranges ثم print preview |
| 51 | الإتاحة | 32 rule accessibility check، alt/tags/headings، HTML report | منفذ | `a11y` rules/tests/report | مفقود | DTO/report HTML إلى SAF، low-level edits؛ لا ادعاء autotag | tagged/untagged، depth/cycle/MCID limits |
| 52 | المعايير | PDF/A-2b/A-3b preflight وconvert | جزئي | `preflight`, docs/gaps؛ levels/profiles أخرى غير موجودة | مفقود | level/report/fixable DTO؛ external validator | clauses/issues ثم convert/reopen؛ fonts غير قابلة للإصلاح |
| 53 | المقارنة | text diff وvisual regions | منفذ | `compare/lib.rs`, tests | مفقود | مدخلان/session، canonical DTO، side-by-side غير مثبت أصلاً | insert/delete/replace/long docs وvisual tolerance |
| 54 | التحسين | image downsample، cleanup، links/unused، progress | منفذ جزئي الصيغ | `optimize`؛ formats unsupported تبقى كما هي | مفقود | job/progress/cancel/save، warning عند discard_tags | report/file size/pixels/tags، cancel وidempotence |
| 55 | الحجم | audit_space حسب الفئات | منفذ | `optimize/audit.rs` | مفقود | session/file length وstable schema | مجموع الفئات، ملفات كبيرة، سماح writer overhead |
| 56 | الأدوات | command registry، menus، shortcuts، enabled/needs | منفذ | `engine/commands.rs`, `ui-egui/commands.rs`, ui-parity | تحسن جزئي في الفرع: سجل 190 ظاهراً؛ 62 ربطاً مباشراً و128 فجوة | إكمال كل route/native adapter واحترام `enabled/Needs` واختبارات السلوك | snapshot registry/schema؛ تنفيذ كل ID ورفض غير المدعوم بصراحة |
| 57 | دورة التطبيق | Home/recent/pinned/last session/save-as/revert | جزئي | `home.rs`, `last_session.rs`, `reopen.rs`; gaps موثقة | مفقود | RecentSessionStore، URI grants، process death | open→kill→restore، pin/clear، atomic save |
| 58 | الأتمتة | headless tools، command batch، MCP/control | منفذ/جزئي | `automation` README/tools/mcp؛ UI control لاحق | جزئي: 153 مخطط أداة عبر JNI؛ لا MCP/Binder أو command parity كامل | Binder/local authenticated IPC، allowlist، timeouts | JSON schemas، hostile args، root/URI confinement |

### ملاحظات على العدّ

- عدد صفوف المصفوفة: **58 قدرة** عبر **10 مناطق**. بعض الصفوف تجمع APIs مترابطة لتبقى المصفوفة قابلة للقراءة؛ لا يمثل الرقم عدد كل API أو كل اختبار في التقارير.
- في هذا baseline، Android **مفقود** في الغالبية الساحقة من الصفوف؛ حالات `جزئي` محصورة في الفتح/تحليل الصفحة/الرستر وبعض التنقل/كلمة المرور. لا ينبغي تحويل هذا العد إلى نسبة تكافؤ كلية.
- `منفذ/جزئي` و`منفذ جزئي الصيغ` تعني أن التقرير نفسه يثبت قدرة أساسية مع حدود معلنة؛ لا تعني اكتمال interoperability.

## 3. فصل فجوات الأصل عن فجوات Android

### فجوات أصلية في PdfCraft

1. **Attachments/Info/XMP typed APIs** غير مثبتة؛ raw COS access لا يساوي feature مستخدم.
2. **Vector shapes** ليست كائنات تحرير مستقلة في `ObjectKind`; لا يجوز ادعاء shape editor.
3. **Annotations**: callouts/polygons/polylines/clouds/carets/attachments وبعض stamps غير مكتملة أو appearance غير قابل لإعادة البناء.
4. **Redaction audit** محدود: لا يثبت كشف كل paths/shadings/patterns أو النص داخل forms أو كل حالات rotation.
5. **Sanitize** لا يغطي كل struct-tree/alt-text/hidden-information categories؛ XFA/JS وAcroForm لها حدود معلنة.
6. **XFA/JavaScript** subset، وbinding وexecEvent وconnectionSet وقياسات الخطوط/إعادة layout ليست Acrobat-complete.
7. **Preflight** يركز على PDF/A-2b/A-3b وقواعد محددة؛ لا PDF/A-1/A-4 أو PDF/X/UA أو profiles كاملة.
8. **Compare/optimize/export** لها حدود fidelity والصيغ؛ DOCX/PPTX/HTML تحويلات lossy، وXDP enum لا يثبت XDP package.
9. **OCR** مثبت English/Latin فقط في التقرير؛ لا دليل على دعم اللغات الأخرى.
10. **SAF/Android transport، Compose UI، Keystore، lifecycle/process death، accessibility instrumentation** ليست فجوات محرك PdfCraft بل فجوات تطبيق Android.

### فجوات النقل إلى Android

1. توجد مساحة Automation/JNI مستمرة متعددة المستندات؛ ما زال lifecycle/process death، cancellation، limits/generation وmemory pressure غير مكتملة.
2. توجد نتائج JSON typed لأدوات Automation ومسار raster ذي packet؛ يلزم توحيد أخطاء كل العمليات وwarnings/progress/cancel لا الاكتفاء برسائل عامة.
3. توجد SAF staging للإدخال وCreateDocument للحفظ/التصدير بنقل chunked؛ لا يثبت ذلك atomic commit/fsync أو إعادة فتح كل output أو crash recovery.
4. توجد Compose للعارض/البحث/نماذج الأدوات وسجل الأوامر؛ ما تزال outline/layers/thumbnails/text selection/zoom المتقدم/accessibility والتخطيطات غير مكتملة.
5. توجد session mutation وundo/redo/dirty عبر Automation؛ 153 أداة عامة لا تعوّض واجهات اللمس المتخصصة ولا تثبت enforcement لكل صلاحيات PDF.
6. أدوات التوقيع والحماية المصدرية غير مدمجة بأمان مع Android Keystore/KeyChain وTrustStore/TSA؛ لا تُخزن مفاتيح خام في Kotlin.
7. يلزم OCR model assets ومسار filesDir، مع Print Framework/ShareSheet/FileProvider.
8. JNI داخل التطبيق يستخدم Workspace root؛ لا يوجد بعد IPC/MCP خارجي أو allowlist/auth policy، ولا ينبغي إضافة قناة تحكم بلا lifecycle/auth.
9. لا تزال اختبارات Android instrumentation للـarm64/x86_64، rotation، background/process death، TalkBack، memory pressure، وSAF permissions مطلوبة.

## 4. المراحل المتبقية بعد الشريحة الحالية

1. **إحكام الجلسة:** lifecycle، limits، cancellation، generation، typed warnings/progress، process death، password/auth/permissions واختبارات الذاكرة.
2. **الحفظ الموثوق:** إكمال مسار `saveFull/saveIncremental` إلى SAF مع commit آمن قدر الإمكان، reopen/round-trip/crash recovery؛ لا تحفظ فوق input URI افتراضياً.
3. **عارض كامل:** PageInfo/transform، tile/preview/cache، zoom/pan/layout، thumbnails، text/search، links/outline/layers، مع Compose accessibility. العارض الحالي وحده ليس تكافؤاً.
4. **إغلاق سجل الأوامر:** إكمال كل IDs الـ190 عبر UI adapter أو tool mapping مطابق، بما في ذلك الأوامر الـ128 غير المربوطة حالياً، ثم اختبار `enabled/Needs` والنتائج.
5. **التنظيم والتاريخ:** page edits، PageBox، import/extract/split/combine، undo/redo وdirty/generation، مع permission enforcement.
5. **المحتوى والتعليقات:** editable text/images، annotations/replies، diagnostics للخطوط/appearance؛ feature-gate ما هو أصلاً جزئي أو غير واضح.
6. **النماذج وXFA/JS:** AcroForm typed state، validation/actions، data exchange، sandbox/caps وCompose requests؛ لا تُخفِ حدود XFA/JS المصدرية.
7. **الحماية والتوقيع والتنقيح:** encryption output، redaction fail-closed، audit findings، Keystore signing، TrustStore/TSA/DSS؛ atomic output والتحقق قبل الكشف.
8. **OCR والإنشاء/التصدير/الطباعة/المشاركة:** assets، image/text creation، XFDF/data/export، PrintDocumentAdapter، ShareSheet، مع نتائج `implemented/partial/unsupported` وتحذيرات lossiness.
9. **الإتاحة والمعايير والمقارنة والتحسين:** a11y reports/alt/tags، preflight، compare، optimize، audit_space، ثم تقارير HTML/SAF.
10. **الاعتماد:** إكمال registry-driven UX، schemas، hostile-input tests، regression corpus، أجهزة فعلية، ومراجعة بشرية لكل claim.

## 5. معايير قبول ومخاطر

- لا يُسمى أي صف `implemented` على Android لمجرد وجود Rust API أو اسم اختبار؛ يلزم binding، UI/contract، save/reopen عند اللزوم، واختبار فعلي.
- لا تُعامل نتيجة فتح الملف أو نجاح raster على أنها سلامة، قابلية تحرير، أو تكافؤ Acrobat. خصوصاً repair وredaction وsigning يجب أن تعيد تقارير typed وتفشل مغلقاً.
- يجب مقارنة COS semantics، extracted text، geometry، raster ضمن tolerances معلنة، وليس page count فقط؛ واستخدام independent parser/validator حيث يلزم.
- يجب حماية كلمات المرور والمفاتيح وبيانات redaction من logs/temporary files، وعدم إبقاء plaintext في output incremental عند متطلبات السرية.
- يجب اختبار الملفات الكبيرة/الخبيثة، cycles/depth/oversize، process death، cancellation، memory peak، URI permission، rotation، TalkBack، arm64 وx86_64.
- أي feature مصدرية `جزئي` أو `غير واضح` يجب أن تظهر للمستخدم كـpartial/unsupported مع السبب، لا أن تتحول إلى نجاح صامت.

## 6. خلاصة الحالة الحالية

الفرع الحالي تجاوز العارض التجريبي الأولي: فيه Workspace Rust مستمر، أدوات Automation، بحث/تاريخ/حفظ أولي، وسجل أوامر ظاهر. لكنه **ليس تكافؤاً كاملاً**: فقط 62 من 190 أمراً لها ربط مباشر، والعرض/الـSAF/الأمن وواجهات اللمس ليست متحققة على أجهزة، كما لا تزال بعض قدرات PdfCraft الأصلية جزئية. لا تُعلن نسبة تكافؤ ولا release production قبل إغلاق قائمة الأوامر، اختبارات regression/interoperability وinstrumentation، والتحقق اليدوي من الملفات الحساسة.
