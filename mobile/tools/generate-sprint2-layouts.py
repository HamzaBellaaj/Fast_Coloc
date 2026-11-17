"""Reproducible native XML resources. No runtime UI generation."""
from pathlib import Path
from xml.sax.saxutils import escape
from xml.dom import minidom

res = Path(__file__).resolve().parents[1] / 'app/src/main/res'
strings = {}
def text(key, value):
    strings[key] = value
    return '@string/' + key
def tag(kind, attrs='', children=''):
    return f'<{kind} {attrs}>{children}</{kind}>'
size = 'android:layout_width="match_parent" android:layout_height="wrap_content"'
def label(value, key=None, ident=None, title=False):
    key = key or 's2_' + (ident or str(len(strings)))
    return tag('TextView', f'{size} '+(f'android:id="@+id/{ident}" ' if ident else '')+f'android:text="{text(key,value)}" android:textAppearance="@style/TextAppearance.FastColoc.{"Title" if title else "Body"}" android:layout_marginTop="12dp"')
def button(ident, value):
    return tag('com.google.android.material.button.MaterialButton',f'{size} android:id="@+id/{ident}" android:text="{text("s2_"+ident,value)}" android:minHeight="48dp" android:layout_marginTop="8dp" style="@style/Widget.FastColoc.Button"')
def field(ident, value, input_type='text', maximum=250):
    return label(value,'s2_label_'+ident)+tag('com.google.android.material.textfield.TextInputLayout',f'{size} style="@style/Widget.FastColoc.Field"',tag('com.google.android.material.textfield.TextInputEditText',f'{size} android:id="@+id/{ident}" android:hint="{text("s2_hint_"+ident,value)}" android:inputType="{input_type}" android:maxLength="{maximum}" android:minHeight="50dp" android:saveEnabled="true"'))
def spinner(ident, value):
    return label(value,'s2_label_'+ident)+tag('Spinner',f'{size} android:id="@+id/{ident}" android:minHeight="50dp" android:contentDescription="{text("s2_hint_"+ident,value)}"')
def linear(children, ident=None, extra=''):
    return tag('LinearLayout',f'{size} android:orientation="vertical" '+(f'android:id="@+id/{ident}" ' if ident else '')+extra,children)
def xml(name,body,scroll=True):
    inner=linear(body,extra='android:padding="20dp"')
    if scroll:
        inner=tag('androidx.core.widget.NestedScrollView','android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true"',inner)
    (res/'layout'/name).write_text('<?xml version="1.0" encoding="utf-8"?>\n'+inner.replace(' ', ' ',1).replace('>', '>\n',1).replace('<androidx.core.widget.NestedScrollView ','<androidx.core.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" ',1) if scroll else '',encoding='utf8')
def write(name,root):
    root=root.replace(' ',' xmlns:android="http://schemas.android.com/apk/res/android" xmlns:app="http://schemas.android.com/apk/res-auto" ',1)
    formatted=minidom.parseString(root).toprettyxml(indent='    ',encoding='utf-8').decode('utf-8')
    (res/'layout'/name).write_text(formatted,encoding='utf8')
def scroll(body):
    return tag('androidx.core.widget.NestedScrollView','android:layout_width="match_parent" android:layout_height="match_parent" android:fillViewport="true"',linear(body,extra='android:padding="20dp"'))
def rv(ident,height='wrap_content'):
    return tag('androidx.recyclerview.widget.RecyclerView',f'android:id="@+id/{ident}" android:layout_width="match_parent" android:layout_height="{height}" android:clipToPadding="false" android:nestedScrollingEnabled="false"')

write('fragment_listing_catalog.xml',tag('LinearLayout','android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="16dp"',
    label('Trouve ta coloc.','s2_catalog_title','tvTitle',True)+label('Chargement…',ident='tvState')+
    tag('ProgressBar','android:id="@+id/listingProgress" android:layout_width="40dp" android:layout_height="40dp" android:layout_gravity="center_horizontal" android:visibility="gone"')+
    button('btnNewListing','Publier une annonce ↗')+tag('androidx.recyclerview.widget.RecyclerView','android:id="@+id/rvListings" android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:clipToPadding="false"')+
    tag('LinearLayout',f'{size} android:orientation="horizontal"',tag('com.google.android.material.button.MaterialButton','android:id="@+id/btnPrevious" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="'+text('s2_previous','← Précédent')+'"')+tag('com.google.android.material.button.MaterialButton','android:id="@+id/btnNextPage" android:layout_width="0dp" android:layout_weight="1" android:layout_height="wrap_content" android:text="'+text('s2_next','Suivant →')+'"'))))
write('item_listing.xml',linear(tag('ImageView','android:id="@+id/ivPhoto" android:layout_width="match_parent" android:layout_height="180dp" android:scaleType="centerCrop" android:background="@color/fc_pink" android:contentDescription="'+text('s2_photo','Photo de l’annonce')+'"')+label('',ident='tvTitle',title=True)+label('',ident='tvPlace')+label('',ident='tvRent')+label('',ident='tvInfo')+label('',ident='tvStatus')+button('btnFavorite','♡ Ajouter aux favoris'),extra='android:background="@drawable/bg_fc_card" android:padding="16dp" android:layout_marginBottom="16dp"'))
filters=spinner('spKind','Type d’annonce')+spinner('spRegion','Région')+spinner('spLocality','Ville / localité')
for ident,value,kind in [('etMinRent','Loyer minimum (TND)','numberDecimal'),('etMaxRent','Loyer maximum (TND)','numberDecimal'),('etMinCapacity','Capacité minimum','number'),('etPropertyFilter','Type de logement','text'),('etRoommateFilter','Type de colocataire','text'),('etEquipmentFilter','Équipements séparés par une virgule','text'),('etAvailableFilter','Disponible le (AAAA-MM-JJ)','date')]:filters+=field(ident,value,kind)
filters+=spinner('spSort','Trier les annonces')
write('dialog_listing_filters.xml',scroll(filters))
detail=label('',ident='tvTitle',title=True)+label('',ident='tvRent')+label('',ident='tvState')+button('btnRefreshListing','Actualiser la fiche')+rv('rvPhotos')
for ident in ['tvDescription','tvPlace','tvInfo','tvEquipment','tvAvailable','tvExpiry']:detail+=label('',ident=ident)
detail+=button('btnFavorite','♡ Ajouter aux favoris')+button('btnEditListing','Modifier mon annonce')+button('btnRenewListing','Publier / renouveler')+button('btnDeleteListing','Supprimer mon annonce')
write('fragment_listing_detail.xml',scroll(detail))
write('item_listing_photo.xml',linear(tag('ImageView','android:id="@+id/ivPhoto" android:layout_width="match_parent" android:layout_height="160dp" android:scaleType="centerCrop" android:contentDescription="@string/s2_photo" android:background="@color/fc_lilac"')+label('',ident='tvCaption')+button('btnDeletePhoto','Supprimer cette photo'),extra='android:padding="4dp"'))
one=spinner('spKind','Type d’annonce')+field('etListingTitle','Titre','text',160)+field('etListingDescription','Description','textMultiLine',10000)+field('etPropertyType','Type de logement','text',80)+field('etRoommateType','Type de colocataire (facultatif)','text',80)
two=spinner('spListingLocality','Ville / localité')+field('etStreet','Adresse ou secteur recherché')+field('etLatitude','Latitude (30 à 38)','numberDecimal|numberSigned')+field('etLongitude','Longitude (7 à 12)','numberDecimal|numberSigned')
three=''
for ident,value,kind in [('etRent','Loyer / budget mensuel (TND)','numberDecimal'),('etCapacity','Capacité (1 à 50 personnes)','number'),('etEquipment','Équipements séparés par une virgule','text'),('etAvailableFrom','Disponible dès le (AAAA-MM-JJ)','date'),('etAvailableUntil','Jusqu’au (AAAA-MM-JJ, facultatif)','date'),('etPublicationDays','Durée de publication (1 à 365 jours)','number')]:three+=field(ident,value,kind,2400 if ident=='etEquipment' else 250)
editor=label('Ton annonce, à ta façon.','s2_editor_title',title=True)+label('Étape 1 / 3',ident='tvStep')+label('',ident='tvState')+linear(one,'sectionPresentation')+linear(two,'sectionLocation','android:visibility="gone"')+linear(three,'sectionConditions','android:visibility="gone"')+button('btnBackStep','← Retour')+button('btnNextStep','Continuer →')+button('btnSaveListing','Enregistrer le brouillon')
editor+=linear(label('Les photos de ta coloc','s2_gallery_title',title=True)+label('Enregistre les modifications avant d’ajouter des photos. Jusqu’à 10 photos, 5 MiB chacune.','s2_gallery_help')+rv('rvPhotos')+field('etPhotoCaption','Légende de la prochaine photo','text',500)+button('btnAddListingPhoto','Ajouter une photo')+button('btnPublishListing','Publier / renouveler mon annonce'),'sectionPhotos','android:visibility="gone"')
write('fragment_listing_editor.xml',scroll(editor))
strings['s2_price']='%.3f TND / mois'
(res/'values'/'strings_sprint2.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'+''.join(f'<string name="{k}">{escape(v)}</string>\n' for k,v in strings.items())+'</resources>',encoding='utf8')
print('Sprint 2 native XML resources generated')
